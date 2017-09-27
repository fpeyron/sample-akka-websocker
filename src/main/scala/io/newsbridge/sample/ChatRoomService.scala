package io.newsbridge.sample

import java.util.concurrent.TimeUnit

import akka.NotUsed
import akka.actor.{ActorRef, ActorSystem, PoisonPill, Props}
import akka.http.scaladsl.model.ws.{Message, TextMessage}
import akka.http.scaladsl.server.Directives._
import akka.stream.OverflowStrategy
import akka.stream.scaladsl.{Flow, Sink, Source}

import scala.concurrent.duration.FiniteDuration

class ChatRoomService(chatRoomActor: ActorRef)(implicit system: ActorSystem) {

  // implicit val timeout: Timeout = Timeout(2.seconds)

  val route = path("chat" / Segment) { channel =>
    get {
      handleWebSocketMessages(newUser(channel))
    }
  }

  def newUser(channel: String): Flow[Message, Message, NotUsed] = {
    // new connection - new user actor
    val userActor = system.actorOf(Props(new UserActor(chatRoomActor)))

    val incomingMessages: Sink[Message, NotUsed] =
      Flow[Message].map {
        // transform websocket message to domain message
        case TextMessage.Strict(text) => UserActor.IncomingMessage(text)
      }.to(Sink.actorRef[UserActor.IncomingMessage](userActor, PoisonPill))


    val outgoingMessages: Source[Message, NotUsed] = Source
      .actorRef[UserActor.OutgoingMessage](10, OverflowStrategy.fail)
      // give the user actor a way to send messages out
      .mapMaterializedValue { outActor =>
      userActor ! UserActor.Connected(outActor, channel)
      NotUsed
    }
      // transform domain message to web socket message
      .map((outMsg: UserActor.OutgoingMessage) => TextMessage(outMsg.text))
      // timeout
      .keepAlive(FiniteDuration(60, TimeUnit.SECONDS), () => TextMessage.Strict("Heart Beat"))

    // then combine both to a flow
    Flow.fromSinkAndSource(incomingMessages, outgoingMessages)
  }
}
