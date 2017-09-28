package io.newsbridge.sample.back

import javax.ws.rs.Path
import javax.ws.rs.core.MediaType

import akka.actor.ActorRef
import akka.http.scaladsl.model.{HttpResponse, StatusCodes}
import akka.http.scaladsl.server.{Directives, Route}
import akka.util.Timeout
import com.fasterxml.jackson.annotation.JsonAnyGetter
import io.newsbridge.sample.ChatRoomActor.{GetChannels, TriggerMessage}
import io.newsbridge.sample.back.BackService._
import io.newsbridge.sample.{CorsSupport, DefaultJsonFormats}
import io.swagger.annotations._
import spray.json.RootJsonFormat

import scala.concurrent.ExecutionContext


@Api(value = "/apps", produces = MediaType.APPLICATION_JSON)
@Path("/apps")
class BackService(chatRoomActor: ActorRef)(implicit executionContext: ExecutionContext)
  extends Directives with DefaultJsonFormats with CorsSupport {

  import akka.pattern.ask

  import scala.concurrent.duration._

  implicit val timeout: Timeout = Timeout(2.seconds)

  implicit val getChannelResponse: RootJsonFormat[GetChannelResponse] = jsonFormat3(GetChannelResponse)
  implicit val findChannelsResponseDetail: RootJsonFormat[FindChannelsResponseDetail] = jsonFormat1(FindChannelsResponseDetail)
  implicit val findChannelsResponse: RootJsonFormat[FindChannelsResponse] = jsonFormat1(FindChannelsResponse)
  implicit val pushEventRequest: RootJsonFormat[PushEventRequest] = jsonFormat4(PushEventRequest)
  implicit val pushEventsRequest: RootJsonFormat[PushEventsRequest] = jsonFormat1(PushEventsRequest)
  implicit val pushEventsRequestDetail: RootJsonFormat[PushEventsRequestDetail] = jsonFormat4(PushEventsRequestDetail)


  val route: Route = findChannels ~ getChannel ~ pushEvent ~ pushEvents


  @Path("/channels")
  @ApiOperation(value = "find channels", notes = "", nickname = "findChannels", httpMethod = "GET")
  @ApiResponses(Array(
    new ApiResponse(code = 200, message = "Return list of channels", response = classOf[FindChannelsResponse]),
    new ApiResponse(code = 500, message = "Internal server error")
  ))
  def findChannels: Route = path("apps" / "channels") {
    get {
      complete(
        (chatRoomActor ? GetChannels).mapTo[Map[String, Long]] map { res =>
          FindChannelsResponse(channels = res map (channel => channel._1 -> FindChannelsResponseDetail(user_count = channel._2) ))
        }
      )
    }
  }


  @Path("/channels/{id}")
  @ApiOperation(value = "get channel", notes = "", nickname = "getChannel", httpMethod = "GET")
  @ApiResponses(Array(
    new ApiResponse(code = 200, message = "Return channel detail", response = classOf[GetChannelResponse]),
    new ApiResponse(code = 404, message = "Channel not found"),
    new ApiResponse(code = 500, message = "Internal server error")
  ))
  @ApiImplicitParams(Array(
    new ApiImplicitParam(name = "id", value = "name of contact to channel", required = true, dataType = "string", paramType = "path")
  ))
  def getChannel: Route = path("apps" / "channels" / Segment) { id =>
    get {
      complete(
        (chatRoomActor ? GetChannels).mapTo[Map[String, Long]] map { res =>
        res.find(_._1 == id)
          .map(channel => GetChannelResponse(occupied = true, user_count = channel._2, subscription_count = channel._2))
          .getOrElse(GetChannelResponse(occupied = false, user_count = 0l, subscription_count = 0l))
        }
      )
    }
  }


  @Path("/events")
  @ApiOperation(value = "push new Event", notes = "", nickname = "pushEvent", httpMethod = "POST")
  @ApiResponses(Array(
    new ApiResponse(code = 200, message = "Return empty list", responseContainer = "Seq", response = classOf[String]),
    new ApiResponse(code = 500, message = "Internal server error")
  ))
  @ApiImplicitParams(Array(
    new ApiImplicitParam(name = "body", value = "event to push", required = true, dataTypeClass = classOf[PushEventRequest], paramType = "body")
  ))
  def pushEvent: Route = path("apps" / "events") {
    post {
      entity(as[PushEventRequest]) { request =>
        complete {
          chatRoomActor ! TriggerMessage(channels = request.channels, message = request.myData.orNull)
            HttpResponse(
              status = StatusCodes.OK
            )
        }
      }
    }
  }


  @Path("/batch_events")
  @ApiOperation(value = "push batch Events", notes = "", nickname = "pushEvents", httpMethod = "POST")
  @ApiResponses(Array(
    new ApiResponse(code = 200, message = "Return empty list", responseContainer = "Seq", response = classOf[String]),
    new ApiResponse(code = 500, message = "Internal server error")
  ))
  @ApiImplicitParams(Array(
    new ApiImplicitParam(name = "body", value = "list of events to push", required = true, dataTypeClass = classOf[PushEventsRequest], paramType = "body")
  ))
  def pushEvents: Route = path("apps" / "batch_events") {
    post {
      entity(as[PushEventsRequest]) { request =>
        complete {
          Seq[String]()

          HttpResponse(
            status = StatusCodes.OK
          )
        }
      }
    }
  }


}

object BackService {

  case class FindChannelsResponse(
                                   @JsonAnyGetter
                                   @ApiModelProperty(value = "channels", required = true) channels: Map[String, FindChannelsResponseDetail]
                                 ) {
  }

  case class FindChannelsResponseDetail(
                                         @ApiModelProperty(value = "user_count", required = true, example = "11") user_count: Long
                                       ) {
  }

  case class GetChannelResponse(
                                 @ApiModelProperty(value = "occupied", required = true, example = "true") occupied: Boolean,
                                 @ApiModelProperty(value = "user_count", required = true, example = "11") user_count: Long,
                                 @ApiModelProperty(value = "subscription_count", required = true, example = "14") subscription_count: Long
                               ) {
  }

  case class PushEventRequest(
                               @ApiModelProperty(value = "name", required = true, example = "myNewEvent") name: String,
                               @ApiModelProperty(value = "data", required = false, example = "{\"val1\":\"myval1\", \"val\":\"myval2\"}") myData: Option[String],
                               @ApiModelProperty(value = "channels", required = true) channels: Seq[String],
                               @ApiModelProperty(value = "socketId", required = false) socketId: Option[String]
                             ) {
    require(Option(name).exists(_.length > 1), s"name should be more 1 chars: $name")
    require(Option(channels).exists(_.nonEmpty), s"minimum 1 channel should be defined: $channels")
    require(channels.exists(_.nonEmpty), s"channel should be not empty: $channels")
  }

  case class PushEventsRequest(
                                @ApiModelProperty(value = "list of events to push", required = true) batch: String //List[PushEventsRequestDetail]
                              ) {
  }

  case class PushEventsRequestDetail(
                                      @ApiModelProperty(value = "name", required = true, example = "myNewEvent") name: String,
                                      @ApiModelProperty(value = "data", required = false, example = "{\"val1\":\"myval1\", \"val\":\"myval2\"}") myData: Option[String],
                                      @ApiModelProperty(value = "last name", required = true, example = "myChannel1") channel: String,
                                      @ApiModelProperty(value = "socketId", required = false) socketId: Option[String]
                                    ) {
    require(Option(name).exists(_.length > 1), s"name should be more 1 chars: $name")
    require(Option(channel).exists(_.length > 0), s"channel should be not empty: $channel")
  }

}
