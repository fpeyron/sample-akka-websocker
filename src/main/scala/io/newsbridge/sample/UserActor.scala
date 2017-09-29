package io.newsbridge.sample

import akka.NotUsed
import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, PoisonPill, Props}
import akka.http.scaladsl.model.ws.{Message, TextMessage}
import akka.stream.OverflowStrategy
import akka.stream.scaladsl.{Flow, Sink, Source}

object UserActor {

  sealed trait UserEvent

  case class Connected(outgoing: ActorRef, channel: String) extends UserEvent

  case class EventMessage(event: String, data: Option[String]) extends UserEvent

}

class UserActor(chatRoomActor: ActorRef)(implicit system: ActorSystem) extends Actor with ActorLogging {

  import UserActor._

  override def receive: Actor.Receive = {
    case Connected(outgoing, channel: String) =>
      context.become {
        chatRoomActor ! ChatRoomActor.Joined(channel)

        {
          case EventMessage(event, text) =>
            chatRoomActor ! ChatRoomActor.MessageAdded(List(channel), "event", text)

          case message: EventMessage =>
            outgoing ! message
        }
      }
  }


  def newUser(channel: String): Flow[Message, Message, NotUsed] = {
    // new connection - new user actor
    val userActor = system.actorOf(Props(new UserActor(chatRoomActor)))

    val incomingMessages: Sink[Message, NotUsed] =
      Flow[Message].map {
        // transform websocket message to domain message
        case TextMessage.Strict(text) => UserActor.EventMessage("event", Some(text))
      }.to(Sink.actorRef[UserActor.EventMessage](userActor, PoisonPill))

    val outgoingMessages: Source[Message, NotUsed] =
      Source.actorRef[UserActor.EventMessage](10, OverflowStrategy.fail)
        .mapMaterializedValue { outActor =>
          // give the user actor a way to send messages out
          userActor ! UserActor.Connected(outActor, channel)
          NotUsed
        }.map(
        // transform domain message to web socket message
        (outMsg: UserActor.EventMessage) => TextMessage(outMsg.data.orNull))

    // then combine both to a flow
    Flow.fromSinkAndSource(incomingMessages, outgoingMessages)
  }
}

