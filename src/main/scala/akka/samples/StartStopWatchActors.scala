package akka.samples

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Kill, PoisonPill, Props, Terminated}

/**
  * Demo to start, stop and watch actors.
  */

object StartStopWatchActors extends App {

  val system = ActorSystem("StartStopWatchActors")

  /**
    * Parent Companion Object
    */
  object Parent {

    /**
      * Start message to spawn a child
      *
      * @param name
      */
    case class StartChild(name: String)

    /**
      * Stop message to kill a child
      *
      * @param name
      */

    case class StopChild(name: String)

    /**
      * Stop message for the parent
      */
    case object Stop

  }

  /**
    * Parent ActorFs
    */
  class Parent extends Actor with ActorLogging {

    import Parent._

    override def receive: Receive = withChildren(Map())

    /**
      * Method #1 : Using context.stop.
      *
      * @param children
      * @return
      */

    def withChildren(children: Map[String, ActorRef]): Receive = {
      case StartChild(name) =>
        log.info(s"Starting child with the name: $name")
        context.become(withChildren(children + (name -> context.actorOf(Props[Child], name))))
      // Changes the Actor's behavior to become the new 'Receive' (PartialFunction[Any, Unit]) handler.
      // Replaces the current behavior on the top of the behavior stack.
      case StopChild(name) =>
        log.info(s"Stopping child with name: $name")
        val childOption = children.get(name)
        childOption.foreach(childRef => context.stop(childRef))
      // stops the child ActorRef
      case Stop =>
        context.stop(self) // stops the children first recursively, then kill self
    }
  }

  /**
    * Child Actor
    */
  class Child extends Actor with ActorLogging {
    override def receive: Receive = {
      case message => log.info(message.toString)
    }
  }

  import Parent._

  /**
    *
    * Test Code
    *
    */


  /**
    * method # 1 --- Using context.stop
    */
  val parent = system.actorOf(Props[Parent], "parent")
  parent ! StartChild("child1")
  parent ! StartChild("child2")

  val child1 = system.actorSelection("/user/parent/child1")
  val child2 = system.actorSelection("/user/parent/child2")
  child1 ! "Hi child1"
  child2 ! "Hi child2"

  parent ! StopChild("child2")
  for (_ <- 1 to 50) child2 ! "[Child2] --- Are you still there?"

  parent ! Stop
  for (_ <- 1 to 50) parent ! "[Parent] --- Are you still there?"

  /**
    * Method #2 - Using special messages.
    */

  val actor = system.actorOf(Props[Child], "actor")
  actor ! "Hi"
  actor ! PoisonPill // PoisonPill message
  for (_ <- 1 to 50) actor ! "[actor] --- Are you still there?"
  val killedActor = system.actorOf(Props[Child], "killedActor")
  killedActor ! Kill // Kill message will abruptly kill the actor throwing an exception.
  killedActor ! "[KilledActor]--- Are you alive?"

  /**
    * Watcher Actor
    */
  class Watcher extends Actor with ActorLogging {

    import Parent._

    override def receive: Receive = {
      case StartChild(name) =>
        val child = context.actorOf(Props[Child], name)
        log.info(s"Started and watching child $name")
        context.watch(child)
      case Terminated(ref) =>
        log.info(s"$ref is terminated")
      // received when child which is under watch is terminated message.
    }
  }

  val watcher = system.actorOf(Props[Watcher], "watcher")
  watcher ! StartChild("watcherChild")

  val watcherChild = system.actorSelection("/user/watcher/watcherChild")
  Thread.sleep(500)
  watcherChild ! PoisonPill
  Thread.sleep(500)

}
