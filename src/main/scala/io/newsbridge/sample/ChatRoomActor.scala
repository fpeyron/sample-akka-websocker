package io.newsbridge.sample

import akka.actor.{Actor, ActorLogging, ActorRef, Terminated}

class ChatRoomActor extends Actor with ActorLogging {

  import io.newsbridge.sample.ChatRoomActor._

  override def receive: Actor.Receive = {

    case JoinRoom =>
      log.info(s"$sender is Joining")
      // we want to remove the user if it's actor is stopped
      context watch sender
      sender() ! ConnectedUserActor.ConnectionEstablished

    case Terminated(userActor) =>
      log.info(s"$userActor is leaving")
      Chats.removeUser(userActor)

    case msg: PublishMessage =>
      log.debug(s"messageAdded to ${msg.channels.mkString(",")} : ${msg.event} : ${msg.data}")
      msg.channels foreach { channel =>
        Chats.getUsersChannel(channel) foreach { actor =>
          actor ! ConnectedUserActor.MessageReceived(channel = channel, event = msg.event, data = msg.data)
        }
      }

    case SubscribeChannel(channel) =>
      log.info(s"$sender : Subscribe to $channel")
      Chats.addUserChannel(sender, channel)
      sender ! ConnectedUserActor.SubscriptionSucceeded

    case UnsubscribeChannel(channel) =>
      log.info(s"$sender : Unsubscribe to $channel")
      Chats.removeUserChannel(sender, channel)
      sender ! ConnectedUserActor.UnsubscriptionSucceeded

    case GetChannels =>
      sender ! Chats.getChannels
  }

}


object ChatRoomActor {

  sealed trait Command
  case object JoinRoom extends Command
  case class SubscribeChannel(channel: String) extends Command
  case class UnsubscribeChannel(channel: String) extends Command
  case class PublishMessage(channels: List[String], event: String, data: Option[String])

  sealed trait Query
  case object GetChannels extends Query


}

object Chats {

  private var chats = List.empty[(String, ActorRef)]

  def addUserChannel(userActor: ActorRef, channel: String) {
    chats = (channel, userActor) :: chats.filterNot(_._1 == channel)
  }

  def removeUserChannel(userActor: ActorRef, channel: String) {
    chats = chats.filterNot(record => record._1 == channel && record._2 == userActor)
  }

  def removeUser(userActor: ActorRef) {
    chats = chats.filterNot(_._2 == userActor)
  }

  def getUsersChannel(channel: String): List[ActorRef] =
    chats.filter(_._1 == channel).map(_._2)


  def getChannels: Map[String, Long] =
    chats.groupBy(_._1).mapValues(_.map(_._2).length.toLong)
}
