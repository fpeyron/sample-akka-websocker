package io.newsbridge.sample

import java.util.concurrent.TimeUnit

import akka.NotUsed
import akka.actor.{ActorRef, ActorSystem, PoisonPill}
import akka.http.scaladsl.model.ws.{Message, TextMessage}
import akka.http.scaladsl.server.{Directives, Route}
import akka.stream.OverflowStrategy
import akka.stream.scaladsl.{Flow, Sink, Source}
import akka.util.Timeout

import scala.concurrent.ExecutionContext


class ChatRoomService(chatRoomActor: ActorRef)(implicit ec: ExecutionContext, system: ActorSystem) extends Directives with DefaultJsonFormats {
  import scala.concurrent.duration._
  import spray.json._

  implicit val timeout: Timeout = Timeout(2.seconds)
  implicit val incomingMessage = jsonFormat4(ConnectedUserActor.IncomingMessage)
  implicit val outgoingMessage = jsonFormat3(ConnectedUserActor.OutgoingMessage)


  val route: Route = path("chat" ) {
    get {
      handleWebSocketMessages(newUser())
    }
  }

  def newUser(): Flow[Message, Message, NotUsed] = {

    // create a user actor per webSocket connection
    val connectedWsActor = system actorOf(ConnectedUserActor.props(chatRoomActor))

    // incomingMessages representation
    val incomingMessages: Sink[Message, NotUsed] =
      Flow[Message].map {
        case TextMessage.Strict(text) =>
          text.parseJson.convertTo[ConnectedUserActor.IncomingMessage]
        case _ =>
      }.to(Sink.actorRef(connectedWsActor, PoisonPill))

    // outgoingMessages representation
    val outgoingMessages: Source[Message, NotUsed] =
      Source
        .actorRef[ConnectedUserActor.OutgoingMessage](10, OverflowStrategy.fail)
        .mapMaterializedValue { outgoingActor =>
          // you need to send a Connected message to get the actor in a state
          // where it's ready to receive and send messages, we used the mapMaterialized value
          // so we can get access to it as soon as this is materialized
          connectedWsActor ! ConnectedUserActor.Connect(outgoingActor)
          NotUsed
        }
        .map {
          // Domain Model => WebSocket Message
          case msg: ConnectedUserActor.OutgoingMessage =>
            TextMessage(msg.toJson.toString())
        }
        // timeout
        //.keepAlive(FiniteDuration(60, TimeUnit.SECONDS), () => TextMessage.Strict("ping"))

    // then combine both to a flow
    Flow.fromSinkAndSource(incomingMessages, outgoingMessages)
  }
}
