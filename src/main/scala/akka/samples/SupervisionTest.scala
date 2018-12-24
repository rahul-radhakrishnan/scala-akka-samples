package akka.samples


import akka.actor.SupervisorStrategy.{Escalate, Restart, Resume, Stop}
import akka.actor.{Actor, ActorRef, ActorSystem, AllForOneStrategy, OneForOneStrategy, Props, SupervisorStrategy, Terminated}
import akka.testkit.{EventFilter, ImplicitSender, TestKit}
import org.scalatest.{BeforeAndAfterAll, WordSpecLike}

/**
  * The test suite for supervision stratgey
  *
  */

object SupervisionTest {

  /**
    * The test actor class
    */

  class Supervisor extends Actor {
    //override the supervisor strategy with actions for each failures.
    override val supervisorStrategy: SupervisorStrategy = OneForOneStrategy() {
      case _: NullPointerException => Restart
      case _: IllegalArgumentException => Stop
      case _: RuntimeException => Resume
      case _: Exception => Escalate
    }

    override def receive: Receive = {
      case props: Props =>
        val childRef = context.actorOf(props)
        sender() ! childRef
    }
  }

  /**
    * Report message object to get the result
    */
  case object Report

  /**
    * Used for testing the child behaviour on its failure
    */
  class NoDeathOnRestartSupervisor extends Supervisor {
    override def preRestart(reason: Throwable, message: Option[Any]): Unit = {
      // doesn't do anything
    }
  }

  /**
    *
    */
  class AllForOneSupervisor extends Supervisor {
    override val supervisorStrategy = AllForOneStrategy() {
      case _: NullPointerException => Restart
      case _: IllegalArgumentException => Stop
      case _: RuntimeException => Resume
      case _: Exception => Escalate
    }
  }

  /**
    * Test class for the word counting and exception handling
    */

  class WordCounter extends Actor {
    var words = 0

    override def receive: Receive = {
      case "" => throw new NullPointerException("empty string!!!")
      case sentence: String =>
        if (sentence.length > 20) throw new RuntimeException("sentence too big!!!")
        else if (!Character.isUpperCase(sentence(0))) throw new IllegalArgumentException("senetence must star with " +
          "uppercase character")
        else words += sentence.split(" ").length
      case Report => sender() ! words
      case _ => throw new Exception("only strings accepted!!!")
    }
  }

}

/**
  * Test suite class
  */
class SupervisionTest
  extends TestKit(ActorSystem("SuperVisionTest"))
    with ImplicitSender
    with WordSpecLike
    with BeforeAndAfterAll {

  override def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
  }

  import SupervisionTest._

  "A supervisor" should {
    "resume its child in case of a minor fault" in {
      val supervisor = system.actorOf(Props[Supervisor])
      supervisor ! Props[WordCounter]
      val child = expectMsgType[ActorRef]
      child ! "I love India"
      child ! Report
      expectMsg(3)
      var input = ""
      for (_ <- 1 to 21) input = input + "a"
      child ! input
      child ! Report
      expectMsg(3)
    }
    "restart its child in case of an empty string" in {
      val supervisor = system.actorOf(Props[Supervisor])
      supervisor ! Props[WordCounter]
      val child = expectMsgType[ActorRef]
      child ! "I love India"
      child ! Report
      expectMsg(3)
      child ! ""
      child ! Report
      expectMsg(0)
    }
    "terminate the child in case of IllegalArgumentException" in {
      val supervisor = system.actorOf(Props[Supervisor])
      supervisor ! Props[WordCounter]
      val child = expectMsgType[ActorRef]
      watch(child)
      child ! "i love India"
      val terminatedMessage = expectMsgType[Terminated]
      assert(terminatedMessage.actor == child)
    }
    "escalate an error when it got caught by an Exception" in {
      val supervisor = system.actorOf(Props[Supervisor])
      supervisor ! Props[WordCounter]
      val child = expectMsgType[ActorRef]
      watch(child)
      child ! 43
      val terminatedMessage = expectMsgType[Terminated]
      assert(terminatedMessage.actor == child)
    }
    "A kinder supervisor" should {
      "not kill children in case it's restarted or escalates failures" in {
        val supervisor = system.actorOf(Props[NoDeathOnRestartSupervisor], "supervisor")
        supervisor ! Props[WordCounter]
        val child = expectMsgType[ActorRef]

        child ! "I love Akka"
        child ! Report
        expectMsg(3)

        child ! 45
        child ! Report
        expectMsg(0)
      }
    }
    "An all-for-one supervisor" should {
      "apply the all-for-one strategy" in {
        val supervisor = system.actorOf(Props[AllForOneSupervisor], "allForOneSupervisor")
        supervisor ! Props[WordCounter]
        val child = expectMsgType[ActorRef]

        supervisor ! Props[WordCounter]
        val secondChild = expectMsgType[ActorRef]

        secondChild ! "I love Akka"
        secondChild ! Report
        expectMsg(3)

        EventFilter[NullPointerException]() intercept {
          child ! ""
        }

        Thread.sleep(500)
        secondChild ! Report
        expectMsg(0) // must be restarted as the first child had a major failure¬
      }
    }
  }
}


