package io.newsbridge.sample

import akka.actor.{Actor, ActorLogging, ActorRef, Terminated}

class ChatRoomActor extends Actor with ActorLogging {

  import io.newsbridge.sample.ChatRoomActor._

  override def receive: Actor.Receive = {

    case JoinRoom =>
      log.info(s"${sender.toString()} : Joined")
      // we want to remove the user if it's actor is stopped
      context watch sender
      sender ! None

    case Terminated(userActor) =>
      log.info(s"$userActor is leaving")
      Chats.removeUser(userActor)

    case cm: ChatMessage =>
      log.info(s"Received a ChatMessage message from ${sender}")
      Chats.getUsersChannel(sender.toString) foreach (_ ! cm)

    case msg: MessageAdded =>
      log.info(s"${sender.toString()} : messageAdded to ${msg.channels.mkString(",")} : ${msg.event} : ${msg.data}")
      msg.channels foreach (channel =>
        Chats.getUsersChannel(channel) foreach { actor =>
          log.info(s"send message to ${actor.toString()}")
          actor ! ConnectedUserActor.OutgoingMessage(channel = Some(channel), event = msg.event, data = msg.data)}
        )

    case SubscribeChannel(channel) =>
      log.info(s"${sender.toString()} : SubscribeChannel to $channel")
      Chats.addUserChannel(sender, channel)
      sender ! None

    case UnsubscribeChannel(channel) =>
      log.info(s"${sender.toString()} : UnsubscribeChannel to $channel")
      Chats.removeUserChannel(sender, channel)
      sender ! None

    case GetChannels =>
      sender ! Chats.getChannels
  }

}



object ChatRoomActor {

  sealed trait ChannelEvent

  case class JoinRoom(userActor: ActorRef) extends ChannelEvent

  case class SubscribeChannel(channel: String)

  case class UnsubscribeChannel(channel: String)

  case class MessageAdded(channels: List[String], event: String, data: Option[String]) extends ChannelEvent

  sealed trait ChannelQuery

  case object GetChannels extends ChannelQuery

  case class ChatMessage(message: String)

}

object Chats {

  private var chats: Map[String, Set[ActorRef]] = Map.empty[String, Set[ActorRef]]

  def addUserChannel(userActor: ActorRef, channel: String) {
    chats = chats + chats.find(_._1 == channel).map(c => (c._1, c._2 + userActor)).getOrElse((channel, Set(userActor)))
  }

  def removeUserChannel(userActor: ActorRef, channel: String) {
    chats = chats.filterNot(_._1 == channel) ++ chats.filter(_._1 == channel).map(s => (s._1, s._2.filterNot(_ == userActor))).filterNot(_._2.isEmpty)
  }

  def removeUser(userActor: ActorRef): Unit = {
    chats = chats.map(s => (s._1, s._2.filterNot(_ == userActor))).filterNot(_._2.isEmpty)
  }

  def getUsersChannel(channel: String) =
    chats filter (_._1 == channel) flatMap (_._2)


  def getChannels =
    chats map (channel => (channel._1, channel._2.toList.length.toLong))
}
