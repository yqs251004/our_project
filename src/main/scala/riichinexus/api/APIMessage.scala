package riichinexus.api

import cats.effect.IO
import org.http4s.Status
import riichinexus.api.http.RouteSupport
import riichinexus.domain.service.AuthenticationFailure
import upickle.default.*

import scala.reflect.ClassTag

trait APIMessage[Response]:
  def plan(context: ApiPlanContext): IO[Response]

trait APIWithTokenMessage[Response] extends APIMessage[Response]

trait NoRequestAPIMessage[Response] extends APIMessage[Response]

final case class ApiPlanContext(
    support: RouteSupport,
    bearerToken: Option[String]
):
  def requireBearerToken: String =
    bearerToken
      .map(_.trim)
      .filter(_.nonEmpty)
      .getOrElse(throw AuthenticationFailure("Bearer token is required", "missing_token"))

final case class RegisteredAPIMessage(
    apiName: String,
    requiresBearerToken: Boolean,
    successStatus: Status,
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
    build[Message, Response](requiresBearerToken = false, successStatus = Status.Ok)

  def created[Message <: APIMessage[Response], Response](using
      Reader[Message],
      Writer[Response],
      ClassTag[Message]
  ): RegisteredAPIMessage =
    build[Message, Response](requiresBearerToken = false, successStatus = Status.Created)

  def accepted[Message <: APIMessage[Response], Response](using
      Reader[Message],
      Writer[Response],
      ClassTag[Message]
  ): RegisteredAPIMessage =
    build[Message, Response](requiresBearerToken = false, successStatus = Status.Accepted)

  def apiWithToken[Message <: APIWithTokenMessage[Response], Response](using
      Reader[Message],
      Writer[Response],
      ClassTag[Message]
  ): RegisteredAPIMessage =
    build[Message, Response](requiresBearerToken = true, successStatus = Status.Ok)

  def noRequest[Message <: NoRequestAPIMessage[Response], Response](message: => Message)(using
      Writer[Response],
      ClassTag[Message]
  ): RegisteredAPIMessage =
    RegisteredAPIMessage(
      apiName = nameOf[Message],
      requiresBearerToken = false,
      successStatus = Status.Ok,
      planJson = (_, context) => message.plan(context).map(writeJs(_))
    )

  private def build[Message <: APIMessage[Response], Response](
      requiresBearerToken: Boolean,
      successStatus: Status
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
          message <- IO(read[Message](body)(using reader))
          response <- message.plan(context)
        yield writeJs(response)(using writer)
    )

  private def nameOf[Message](using classTag: ClassTag[Message]): String =
    APIMessage.apiNameFromClassName(classTag.runtimeClass.getSimpleName)
