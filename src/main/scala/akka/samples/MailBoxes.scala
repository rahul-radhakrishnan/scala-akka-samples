package akka.samples

import akka.actor.{Actor, ActorLogging, ActorSystem, PoisonPill, Props}
import akka.dispatch.{ControlMessage, PriorityGenerator, UnboundedPriorityMailbox}
import com.typesafe.config.{Config, ConfigFactory}

object MailBoxes extends App {

  val system = ActorSystem("MailBoxes", ConfigFactory.load().getConfig("mailboxes"))

  class SimpleActor extends Actor with ActorLogging {
    override def receive: Receive = {
      case message => log.info(message.toString)
    }
  }

  class TicketPriorityMailBox(settings: ActorSystem.Settings, config: Config)
    extends UnboundedPriorityMailbox(PriorityGenerator {
      case message: String if message.startsWith("[P0]") => 0
      case message: String if message.startsWith("[P1]") => 1
      case message: String if message.startsWith("[P2]") => 2
      case message: String if message.startsWith("[P3]") => 3
      case _ => 4
    })

  val ticketLogger = system.actorOf(Props[SimpleActor].withDispatcher("ticket-dispatcher"))

  //  ticketLogger ! PoisonPill
  //  ticketLogger ! "[P3] p3 message"
  //  ticketLogger ! "[P1] p1 message"
  //  ticketLogger ! "[P2] p2 message"
  //  ticketLogger ! "[P4] p4 message"
  //  ticketLogger ! "[P1] p1 message"
  //  ticketLogger ! "[P2] p2 message"
  //
  //  Thread.sleep(1000)
  //  ticketLogger ! "[P1] p1 message"

  case object ManagementTicket extends ControlMessage

  val controlAwareActor = system.actorOf(Props[SimpleActor].withMailbox("control-mailbox"))
  //  controlAwareActor ! "[P0] this needs to be solved NOW!"
  //  controlAwareActor ! "[P1] do this when you have the time"
  //  controlAwareActor ! ManagementTicket

  val altControlAwareActor = system.actorOf(Props[SimpleActor], "altControlAwareActor")
  altControlAwareActor ! "[P0] this needs to be solved NOW!"
  altControlAwareActor ! "[P1] do this when you have the time"
  altControlAwareActor ! ManagementTicket
}
