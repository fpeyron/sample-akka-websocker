package io.newsbridge.sample

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Terminated}
import io.newsbridge.sample.ChatRoomActor.{Joined, messageAdded}

object ChatRoomActor {

  sealed trait ChannelEvent

  case class Joined(channel: String) extends ChannelEvent

  case class messageAdded(channel: String, message: String)(implicit system: ActorSystem) extends ChannelEvent

  case object CreateUser

}

class ChatRoomActor extends Actor with ActorLogging {

  private var chats: Map[String, Set[ActorRef]] = Map.empty[String, Set[ActorRef]]
  //private var chats: Set[ActorRef] = Set.empty
  //private var subscribers = Set.empty[(String, ActorRef)]
  //private var channels = Set.empty[(String, ActorRef)]

  override def receive = {
    case Joined(channel: String) =>
      log.info(s"${sender().toString()} : Joined")
      chats = chats + chats.find(_._1 == channel).map(c => (c._1, c._2 + sender())).getOrElse((channel, Set(sender())))
      // we also would like to remove the user when its actor is stopped
      context.watch(sender())

    case Terminated(chat) =>
      log.info(s"${sender().toString()} : Terminated")
      chats = chats.map(s => (s._1, s._2.filterNot(_ == chat))).filterNot(_._2.isEmpty)

    case msg: messageAdded =>
      log.info(s"${sender().toString()} : messageAdded : ${msg.message}")
      chats filter (_._1 == msg.channel) flatMap (_._2) foreach (_ ! msg)
  }

}