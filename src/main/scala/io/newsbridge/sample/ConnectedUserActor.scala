package io.newsbridge.sample

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.util.Timeout
import io.newsbridge.sample.ChatRoomActor.{JoinRoom, SubscribeChannel, UnsubscribeChannel}

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}

object ConnectedUserActor {

  sealed trait UserEvent

  case class MessagePushed(event: String, data: Option[String]) extends UserEvent

  sealed trait UserCommand

  case class AddMessage(event: String, data: Option[String]) extends UserEvent


  sealed trait UserMessage

  case class Connected(outgoing: ActorRef)

  case class IncomingMessage(event: String, data: Option[Map[String, String]] = None) extends UserMessage

  case class OutgoingMessage(event: String, channel: Option[String] = None, data: Option[String] = None) extends UserMessage

  def props(chatRoomActor: ActorRef)(implicit ec: ExecutionContext) = Props(new ConnectedUserActor(chatRoomActor))
}

class ConnectedUserActor(chatRoomActor: ActorRef)(implicit ec: ExecutionContext) extends Actor with ActorLogging {

  import ConnectedUserActor._
  import akka.pattern.ask

  import scala.concurrent.duration._

  implicit val timeout: Timeout = Timeout(10.seconds)

  override def receive: Receive = waiting

  def waiting: Receive = {
    // When the user connects, tell the chat room about it so messages
    // sent to the chat room are routed here
    case Connected(userActor) =>
      //log.info(s"WS user: $userActor has connected")
      context become connected(userActor)
      (chatRoomActor ? JoinRoom(userActor)) onComplete {
        case Success(_) => {
          userActor ! OutgoingMessage(event = "connection_established", data = Some(""""{"socket_id":"123712.927749","activity_timeout":120}"""))
        }
        case Failure(_) =>
      }
  }

  def connected(userActor: ActorRef)(implicit ec: ExecutionContext) : Receive = {

    // any messages coming from the WS client will come here and will be sent to the chat room
    case msg: IncomingMessage if msg.event == "pusher:subscribe" =>
      val channel = msg.data.map(_.get("channel")).flatten.map(_.toString()).getOrElse("default")
      (chatRoomActor ? SubscribeChannel(channel)) onComplete {
        case Success(_) =>
          userActor ! (OutgoingMessage(event = "pusher_internal:subscription_succeeded", channel = Some(channel)))
        case Failure(_) =>
      }


    case msg: IncomingMessage if msg.event == "pusher:unsubscribe" =>
      val channel = msg.data.map(_.get("channel")).flatten.map(_.toString()).getOrElse("default")
      (chatRoomActor ? UnsubscribeChannel(channel)) onComplete {
        case Success(_) =>
          userActor ! OutgoingMessage(event = "pusher_internal:unsubscription_succeeded", channel = Some(channel))
        case Failure(_) =>
      }


    case msg: IncomingMessage if msg.event == "pusher:ping" =>
      userActor ! OutgoingMessage(event = "pusher:pong")


    // any messages coming from the chat room need to be sent to the WS Client
    // remember that in this case we are the intermediate bridge and we have to send the message to the ActorPublisher
    // in order for the WS client to receive the message
    case msg: OutgoingMessage =>
      log.debug(s"Intermediate Actor sending message that came from the chat room to $userActor")
      userActor ! msg
  }

}

