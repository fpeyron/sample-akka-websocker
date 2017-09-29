package io.newsbridge.sample

import akka.actor.{Actor, ActorLogging, ActorRef, Terminated}

object ChatRoomActor {

  sealed trait ChannelEvent

  case class Joined(channel: String) extends ChannelEvent

  case class MessageAdded(channels: List[String], event: String, data: Option[String]) extends ChannelEvent

  sealed trait ChannelQuery

  case object GetChannels extends ChannelQuery

}

class ChatRoomActor extends Actor with ActorLogging {

  import io.newsbridge.sample.ChatRoomActor._

  override def receive: Actor.Receive = {

    case Joined(channel) =>
      log.info(s"${sender().toString()} : Joined")
      Chats.addUser(sender, channel)
      // we also would like to remove the user when its actor is stopped
      context.watch(sender)

    case Terminated =>
      log.info(s"${sender().toString()} : Terminated")
      Chats.removeUser(sender)

    case msg: MessageAdded =>
      log.info(s"${sender.toString()} : messageAdded to ${msg.channels.mkString(",")} : ${msg.event} : ${msg.data}")
      msg.channels foreach (channel =>
        Chats.getUsersChannel(channel) foreach (_ ! UserActor.MessagePushed(event = msg.event, data = msg.data))
        )


    case GetChannels =>
      sender ! Chats.getChannels
  }

}

object Chats {

  private var chats: Map[String, Set[ActorRef]] = Map.empty[String, Set[ActorRef]]

  def addUser(userActor: ActorRef, channel: String) {
    chats = chats + chats.find(_._1 == channel).map(c => (c._1, c._2 + userActor)).getOrElse((channel, Set(userActor)))
  }

  def removeUser(userActor: ActorRef): Unit = {
    chats = chats.map(s => (s._1, s._2.filterNot(_ == userActor))).filterNot(_._2.isEmpty)
  }

  def getUsersChannel(channel: String) =
    chats filter (_._1 == channel) flatMap (_._2)


  def getChannels =
    chats map (channel => (channel._1, channel._2.toList.length.toLong))
}