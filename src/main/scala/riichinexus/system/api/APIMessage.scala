package riichinexus.system.api

import java.sql.Connection

import cats.effect.IO
import riichinexus.system.realtime.domain.RealtimeEventBus
import riichinexus.microservices.auth.domain.account.model.AuthenticationFailure
import upickle.default.{Reader, Writer, macroRW, read, writeJs}

import scala.collection.mutable.ArrayBuffer
import scala.deriving.Mirror
import scala.reflect.ClassTag

/** 所有 JSON API 消息的通用执行契约。
  *
  * 具体消息负责把请求体解析后的字段转换成领域调用，并在 `ApiPlanContext` 中访问连接、令牌和提交后回调。
  */
trait APIMessage[Response]:
  def plan(context: ApiPlanContext): IO[Response]

/** 需要 bearer token 才能执行的 API 消息标记 trait。 */
trait APIWithTokenMessage[Response] extends APIMessage[Response]

/** 单次 API 消息执行的请求级上下文。
  *
  * 上下文保存可选令牌、当前数据库连接、实时事件总线和事务提交后的延迟副作用队列。
  */
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

/** 收集事务提交后才允许执行的副作用。 */
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

/** API 消息成功执行时对应的 HTTP 状态语义。 */
enum ApiSuccessStatus:
  case Ok, Created, Accepted

/** 已注册 API 消息的运行时描述。
  *
  * 注册信息包含接口名、是否需要令牌、成功状态，以及从原始 JSON 请求体执行到 JSON 响应的计划函数。
  */
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

  inline def api[Message <: APIMessage[Response], Response](using
      Writer[Response],
      ClassTag[Message],
      Mirror.ProductOf[Message]
  ): RegisteredAPIMessage =
    build[Message, Response](requiresBearerToken = false, successStatus = ApiSuccessStatus.Ok)(
      using macroRW[Message],
      summon[Writer[Response]],
      summon[ClassTag[Message]]
    )

  inline def created[Message <: APIMessage[Response], Response](using
      Writer[Response],
      ClassTag[Message],
      Mirror.ProductOf[Message]
  ): RegisteredAPIMessage =
    build[Message, Response](requiresBearerToken = false, successStatus = ApiSuccessStatus.Created)(
      using macroRW[Message],
      summon[Writer[Response]],
      summon[ClassTag[Message]]
    )

  inline def accepted[Message <: APIMessage[Response], Response](using
      Writer[Response],
      ClassTag[Message],
      Mirror.ProductOf[Message]
  ): RegisteredAPIMessage =
    build[Message, Response](requiresBearerToken = false, successStatus = ApiSuccessStatus.Accepted)(
      using macroRW[Message],
      summon[Writer[Response]],
      summon[ClassTag[Message]]
    )

  inline def apiWithToken[Message <: APIWithTokenMessage[Response], Response](using
      Writer[Response],
      ClassTag[Message],
      Mirror.ProductOf[Message]
  ): RegisteredAPIMessage =
    build[Message, Response](requiresBearerToken = true, successStatus = ApiSuccessStatus.Ok)(
      using macroRW[Message],
      summon[Writer[Response]],
      summon[ClassTag[Message]]
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
      planJson = (body: String, context: ApiPlanContext) =>
        for
          message <- IO.blocking(read[Message](body)(using reader))
          response <- message.plan(context)
          json <- IO.blocking(writeJs(response)(using writer))
        yield json
    )

  private def nameOf[Message](using classTag: ClassTag[Message]): String =
    APIMessage.apiNameFromClassName(classTag.runtimeClass.getSimpleName)
