package io.newsbridge.sample

import java.util.UUID

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.util.Timeout
import io.newsbridge.sample.ChatRoomActor.{JoinRoom, SubscribeChannel, UnsubscribeChannel}

object ConnectedUserActor {

  sealed trait Command
  case class Connect(outgoing: ActorRef) extends Command
  case class IncomingMessage(event: String, data: Option[Map[String, String]] = None, channel: Option[String] = None, message: Option[String] = None) extends Command
  case class OutgoingMessage(event: String, channel: Option[String] = None, data: Option[String] = None) extends Command

  sealed trait Event
  case object ConnectionEstablished extends Event
  case class UnsubscriptionSucceeded(channel: String) extends Event
  case class SubscriptionSucceeded(channel: String) extends Event
  case class MessageReceived(channel: String, event: String, data: Option[String]) extends Event



  def props(chatRoomActor: ActorRef) = Props(new ConnectedUserActor(chatRoomActor))
}

class ConnectedUserActor(chatRoomActor: ActorRef) extends Actor with ActorLogging {

  import ConnectedUserActor._

  import scala.concurrent.duration._

  implicit val timeout: Timeout = Timeout(10.seconds)

  override def receive: Receive = waiting



  def waiting: Receive = {
    // When the user connects, tell the chat room about it so messages
    // sent to the chat room are routed here
    case Connect(userActor) =>
      context.become(connected(userActor))
      chatRoomActor ! JoinRoom
  }

  def connected(userActor: ActorRef, socketId: String = UUID.randomUUID().toString): Receive = {

    // ---------------
    // Commands
    // ---------------

    // Incoming message
    // ------------------------------------------------------------
    case msg: IncomingMessage if msg.event == "pusher:subscribe" =>
      val channel = msg.data.flatMap(_.get("channel")).map(_.toString()).getOrElse("default")
      chatRoomActor ! SubscribeChannel(channel)

    case msg: IncomingMessage if msg.event == "pusher:unsubscribe" =>
      val channel = msg.data.flatMap(_.get("channel")).map(_.toString()).getOrElse("default")
      chatRoomActor ! UnsubscribeChannel(channel)

    case msg: IncomingMessage if msg.event == "pusher:ping" =>
      userActor ! OutgoingMessage(event = "pusher:pong")

    case msg: IncomingMessage if(msg.channel.isDefined) =>
      chatRoomActor ! ChatRoomActor.PublishMessage(channels = List(msg.channel.get), event = msg.event, data = msg.message)


    // Send message
    // ------------------------------------------------------------
    case msg: OutgoingMessage =>
      userActor ! msg


    // ---------------
    // Event
    // ---------------
    case ConnectionEstablished =>
      userActor ! OutgoingMessage(event = "connection_established", data = Some(s""""{"socket_id":"$socketId","activity_timeout":120}"""))

    case SubscriptionSucceeded(channel) =>
      userActor ! OutgoingMessage(event = "pusher_internal:subscription_succeeded", channel = Some(channel))

    case UnsubscriptionSucceeded(channel) =>
      userActor ! OutgoingMessage(event = "pusher_internal:unsubscription_succeeded", channel = Some(channel))

    case MessageReceived(channel, event, data) =>
      userActor !  OutgoingMessage(event = event, channel = Some(channel), data = data)
  }

}

