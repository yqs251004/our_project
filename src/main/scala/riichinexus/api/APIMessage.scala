package riichinexus.api

import java.sql.Connection

import cats.effect.IO
import riichinexus.api.runtime.ApiPlanSupport
import riichinexus.domain.service.AuthenticationFailure
import riichinexus.domain.model.*
import riichinexus.microservices.auth.domain.model.*
import riichinexus.microservices.auth.objects.CurrentSessionView
import upickle.default.*

import scala.reflect.ClassTag

trait APIMessage[Response]:
  def plan(context: ApiPlanContext): IO[Response]

trait APIWithTokenMessage[Response] extends APIMessage[Response]

trait NoRequestAPIMessage[Response] extends APIMessage[Response]

final case class ApiPlanContext(
    support: ApiPlanSupport,
    bearerToken: Option[String],
    connection: Connection
):
  def principal(playerId: PlayerId): AccessPrincipal =
    support.principal(connection, playerId)

  def guestPrincipal(sessionId: GuestSessionId): AccessPrincipal =
    support.guestPrincipal(connection, sessionId)

  def requestActor(guestSessionId: Option[GuestSessionId], operatorId: Option[PlayerId]): AccessPrincipal =
    support.requestActor(connection, guestSessionId, operatorId)

  def resolveCurrentSessionView(
      operatorId: Option[PlayerId],
      guestSessionId: Option[GuestSessionId]
  ): CurrentSessionView =
    support.resolveCurrentSessionView(connection, operatorId, guestSessionId)

  def requireBearerToken: String =
    bearerToken
      .map(_.trim)
      .filter(_.nonEmpty)
      .getOrElse(throw AuthenticationFailure("Bearer token is required", "missing_token"))

enum ApiSuccessStatus:
  case Ok, Created, Accepted

final case class RegisteredAPIMessage(
    apiName: String,
    requiresBearerToken: Boolean,
    successStatus: ApiSuccessStatus,
    planJson: (String, ApiPlanContext) => IO[ujson.Value]
)

object APIMessage:

  private[api] def apiNameFromClassName(className: String): String =
    val objectName = className.stripSuffix("$")
    val baseName = objectName.stripSuffix("APIMessage")
    s"${baseName}API".toLowerCase

object RegisteredAPIMessage:

  def api[Message <: APIMessage[Response], Response](using
      Reader[Message],
      Writer[Response],
      ClassTag[Message]
  ): RegisteredAPIMessage =
    build[Message, Response](requiresBearerToken = false, successStatus = ApiSuccessStatus.Ok)

  def created[Message <: APIMessage[Response], Response](using
      Reader[Message],
      Writer[Response],
      ClassTag[Message]
  ): RegisteredAPIMessage =
    build[Message, Response](requiresBearerToken = false, successStatus = ApiSuccessStatus.Created)

  def accepted[Message <: APIMessage[Response], Response](using
      Reader[Message],
      Writer[Response],
      ClassTag[Message]
  ): RegisteredAPIMessage =
    build[Message, Response](requiresBearerToken = false, successStatus = ApiSuccessStatus.Accepted)

  def apiWithToken[Message <: APIWithTokenMessage[Response], Response](using
      Reader[Message],
      Writer[Response],
      ClassTag[Message]
  ): RegisteredAPIMessage =
    build[Message, Response](requiresBearerToken = true, successStatus = ApiSuccessStatus.Ok)

  def noRequest[Message <: NoRequestAPIMessage[Response], Response](message: => Message)(using
      Writer[Response],
      ClassTag[Message]
  ): RegisteredAPIMessage =
    RegisteredAPIMessage(
      apiName = nameOf[Message],
      requiresBearerToken = false,
      successStatus = ApiSuccessStatus.Ok,
      planJson = (_, context) =>
        for
          response <- message.plan(context)
          json <- IO.blocking(writeJs(response))
        yield json
    )

  private def build[Message <: APIMessage[Response], Response](
      requiresBearerToken: Boolean,
      successStatus: ApiSuccessStatus
  )(using
      reader: Reader[Message],
      writer: Writer[Response],
      classTag: ClassTag[Message]
  ): RegisteredAPIMessage =
    RegisteredAPIMessage(
      apiName = nameOf[Message],
      requiresBearerToken = requiresBearerToken,
      successStatus = successStatus,
      planJson = (body, context) =>
        for
          message <- IO.blocking(read[Message](body)(using reader))
          response <- message.plan(context)
          json <- IO.blocking(writeJs(response)(using writer))
        yield json
    )

  private def nameOf[Message](using classTag: ClassTag[Message]): String =
    APIMessage.apiNameFromClassName(classTag.runtimeClass.getSimpleName)
