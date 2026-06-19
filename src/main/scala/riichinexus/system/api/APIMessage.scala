package riichinexus.system.api

import java.sql.Connection

import cats.effect.IO
import riichinexus.system.realtime.domain.RealtimeEventBus
import riichinexus.microservices.auth.domain.AuthenticationFailure
import upickle.default.{Reader, Writer, read, writeJs}

import scala.collection.mutable.ArrayBuffer
import scala.reflect.ClassTag

trait APIMessage[Response]:
  def plan(context: ApiPlanContext): IO[Response]

trait APIWithTokenMessage[Response] extends APIMessage[Response]

final case class ApiPlanContext(
    bearerToken: Option[String],
    connection: Connection,
    realtimeEventBus: RealtimeEventBus = RealtimeEventBus.empty,
    postCommitHooks: Option[ApiPostCommitHooks] = None
):

  def afterCommit(effect: IO[Unit]): IO[Unit] =
    postCommitHooks match
      case Some(hooks) => IO.delay(hooks.add(effect))
      case None        => effect

final class ApiPostCommitHooks:
  private val hooks = ArrayBuffer.empty[IO[Unit]]

  def add(effect: IO[Unit]): Unit =
    hooks.synchronized {
      hooks += effect
      ()
    }

  def drain: Vector[IO[Unit]] =
    hooks.synchronized {
      val effects = hooks.toVector
      hooks.clear()
      effects
    }

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

object ApiPlanContext:

  def requireBearerToken(context: ApiPlanContext): String =
    context.bearerToken
      .map(_.trim)
      .filter(_.nonEmpty)
      .getOrElse(throw AuthenticationFailure("Bearer token is required", "missing_token"))

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
      planJson = (body: String, context: ApiPlanContext) =>
        for
          message <- IO.blocking(read[Message](body)(using reader))
          response <- message.plan(context)
          json <- IO.blocking(writeJs(response)(using writer))
        yield json
    )

  private def nameOf[Message](using classTag: ClassTag[Message]): String =
    APIMessage.apiNameFromClassName(classTag.runtimeClass.getSimpleName)
