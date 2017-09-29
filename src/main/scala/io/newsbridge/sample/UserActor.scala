package io.newsbridge.sample

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem}

object UserActor {

  sealed trait UserEvent

  case class Connected(outgoing: ActorRef, channel: String) extends UserEvent

  case class MessagePushed(event: String, data: Option[String]) extends UserEvent

  sealed trait UserCommand

  case class AddMessage(event: String, data: Option[String]) extends UserEvent

}

class UserActor(chatRoomActor: ActorRef)(implicit system: ActorSystem) extends Actor with ActorLogging {

  import UserActor._

  override def receive: Actor.Receive = {

    case Connected(outgoing, channel: String) =>
      context.become {
        chatRoomActor ! ChatRoomActor.Joined(channel)

        {
          case AddMessage(event, text) =>
            chatRoomActor ! ChatRoomActor.MessageAdded(List("technical"), event, text)

          case msg: MessagePushed =>
            outgoing ! msg
        }
      }
  }

}

