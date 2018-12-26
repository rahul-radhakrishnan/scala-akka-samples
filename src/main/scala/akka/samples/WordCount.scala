package akka.samples


import akka.actor.{Actor, ActorRef, ActorSystem, Props}

/**
  * A simple master-slave word count actor system in round-robin
  *
  */
object WordCount extends App {

  /**
    *
    */
  object WordCountMaster {

    /**
      * Message to initialize the children actors.
      *
      * @param nChildren
      */
    case class Initialize(nChildren: Int)

    /**
      * Message to start word count task.
      *
      * @param id
      * @param text
      */
    case class WordCountTask(id: Int, text: String)

    /**
      * Message to get the result
      *
      * @param id
      * @param count
      */
    case class WordCountReply(id: Int, count: Int)

  }

  /**
    *
    */

  class WordCountMaster extends Actor {

    import WordCountMaster._

    override def receive: Receive = {
      case Initialize(nChildren) =>
        val childrenRefs = for (i <- 1 to nChildren) yield context.actorOf(Props[WordCountWorker], s"wcw_$i")
        context.become(withChildren(childrenRefs, 0, 0, Map()))
    }

    /**
      * The Receive method used to ro
      * @param childrenRefs
      * @param currentChildIndex
      * @param currentTaskId
      * @param requestmap
      * @return PartialFunction[Any, Unit]
      */
    def withChildren(childrenRefs: Seq[ActorRef],
                     currentChildIndex: Int,
                     currentTaskId: Int,
                     requestmap: Map[Int, ActorRef]): Receive = {
      case text: String =>
        println(s"[master] have received : $text - Sending it to child $currentChildIndex")
        val originalSender = sender()
        val task = WordCountTask(currentTaskId, text)
        val childRef = childrenRefs(currentChildIndex)
        childRef ! task
        val nextChildIndex = (currentChildIndex + 1) % childrenRefs.length
        val newTaskId = currentTaskId + 1
        val newRequestMap = requestmap + (currentTaskId -> originalSender)
        context.become(withChildren(childrenRefs, nextChildIndex, newTaskId, newRequestMap))
      case WordCountReply(id, count) =>
        println(s"[master] received a reply for the task id $id with $count")
        val originalSender = requestmap(id)
        originalSender ! count
        context.become(withChildren(childrenRefs, currentChildIndex, currentTaskId, requestmap - id))
    }
  }

  /**
    *
    */
  class WordCountWorker extends Actor {

    import WordCountMaster._

    override def receive: Receive = {
      case WordCountTask(id, text) =>
        println(s"${self.path} I have receieved a task with $text")
        sender() ! WordCountReply(id, text.split(" ").length)
    }
  }

  /**
    * Test class actor
    */
  class TestActor extends Actor {

    import WordCountMaster._

    var result: Int = 0

    override def receive: Receive = {
      case "go" =>
        val master = context.actorOf(Props[WordCountMaster], "master")
        master ! Initialize(3)
        val texts = List("I love Akka", "I love Scala", "Java is great", "Linux is awesome")
        texts.foreach(t => master ! t)
      case count: Int =>
        println(s"[test actor] I received a reply : $count")
        result += count
      case "result" =>
        println(s"[result] : $result")
    }
  }

  val system = ActorSystem("roundRobinWordCount")
  val testActor = system.actorOf(Props[TestActor], "testActor")
  testActor ! "go"
  Thread.sleep(10000)
  testActor ! "result"

}
