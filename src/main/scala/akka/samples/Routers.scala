package akka.samples

import akka.actor.{Actor, ActorLogging, ActorSystem, Props, Terminated}
import akka.routing._
import com.typesafe.config.ConfigFactory

/**
  * Routers sample code
  */

object Routers extends App {

  class Master extends Actor {

    private val slaves = for (i <- 1 to 5) yield {
      val slave = context.actorOf(Props[Slave], s"slave$i")
      context.watch(slave)
      ActorRefRoutee(slave)
    }

    private val router = Router(RoundRobinRoutingLogic(), slaves)


    override def receive: Receive = {
      case message =>
        router.route(message, sender())
      case Terminated(ref) =>
        router.removeRoutee(ref)
        val newSlave = context.actorOf(Props[Slave])
        context.watch(newSlave)
        router.addRoutee(newSlave)
    }
  }

  class Slave extends Actor with ActorLogging {
    override def receive: Receive = {
      case message =>
        log.info(message.toString)
    }
  }

  val system = ActorSystem("routers", ConfigFactory.load().getConfig("routers"))
  val master = system.actorOf(Props[Master], "master")
  for (i <- 1 to 10) {
    master ! s"message$i"
  }
  val poolMaster = system.actorOf(FromConfig.props(Props[Slave]), "poolMaster")

  val poolMaster1 = system.actorOf(RoundRobinPool(5).props(Props[Slave]), "poolMaster1")
  for (i <- 1 to 10) {
    poolMaster ! s"message$i"
  }

  val slaveList = (1 to 5).map(i => system.actorOf(Props[Slave], s"slave_$i")).toList

  val slavePaths = slaveList.map(slaveRef => slaveRef.path.toString)

  val groupMaster = system.actorOf(RoundRobinGroup(slavePaths).props())

  for (i <- 1 to 10) {
    groupMaster ! s"message$i"
  }

  val groupMaster2 = system.actorOf(FromConfig.props(), "groupMaster")

  for (i <- 1 to 10) {
    groupMaster2 ! s"message$i"
  }

  groupMaster2 ! Broadcast("hello all")
}
