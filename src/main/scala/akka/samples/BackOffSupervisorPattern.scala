package akka.samples

import java.io.File

import akka.actor.SupervisorStrategy.Stop
import akka.actor.{Actor, ActorLogging, ActorSystem, OneForOneStrategy, Props}
import akka.pattern.{Backoff, BackoffSupervisor}

import scala.concurrent.duration._
import scala.io.Source

object BackOffSupervisorPattern extends App {

  class EagerFBPActor extends FileBasedPersistentActor {
    override def preStart(): Unit = {
      log.info("Eager actor starting")
      dataSource = Source.fromFile(new File("src/main/resources/testfiles/test_.txt"))
    }
  }

  case object ReadFile

  class FileBasedPersistentActor extends Actor with ActorLogging {

    var dataSource: Source = null

    override def preStart(): Unit =
      log.info("Persistent Actor starting!!!")

    override def postStop(): Unit =
      log.warning("Persistent Actor has stopped!!!")

    override def preRestart(reason: Throwable, message: Option[Any]): Unit =
      log.warning("Persistent Actor restarting!!!")

    override def receive: Receive = {
      case ReadFile =>
        if (dataSource == null)
          dataSource = Source.fromFile(new File("src/main/resources/testfiles/test_.txt"))
        log.info("Date: " + dataSource.getLines().toList)
    }
  }

  val system = ActorSystem("BackOffSupervisorPattern")
  //  val simpleActor = system.actorOf(Props[FileBasedPersistentActor], "simpleActor")
  // simpleActor ! ReadFile

  val simpleSupervisorProps = BackoffSupervisor.props(
    Backoff.onFailure(
      Props[FileBasedPersistentActor],
      "simpleBackoffActor",
      3 seconds,
      30 seconds,
      0.2
    )
  )

  //  val simpleBackOffActor = system.actorOf(simpleSupervisorProps, "simpleSupervisor")
  //  simpleBackOffActor ! ReadFile

  val stopSupervisorProps = BackoffSupervisor.props(
    Backoff.onStop(
      Props[FileBasedPersistentActor],
      "stopBackoffActor",
      3 seconds,
      30 seconds,
      0.2
    ).withSupervisorStrategy(
      OneForOneStrategy() {
        case _ => Stop
      }
    )
  )

  //  val stopSupervisor = system.actorOf(stopSupervisorProps, "stopSupervisor")
  //  stopSupervisor ! ReadFile

  // ActorInitializationException => STOP

  val repeatedSupervisorProps = BackoffSupervisor.props(
    Backoff.onStop(
      Props[EagerFBPActor],
      "eagerActor",
      1 second,
      16 seconds,
      0.1
    )
  )
  val repeatedSupervisor = system.actorOf(repeatedSupervisorProps, "eagerSupervisor")

}
