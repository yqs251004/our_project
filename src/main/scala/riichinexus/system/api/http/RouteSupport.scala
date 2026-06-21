package riichinexus.system.api.http

import cats.effect.IO
import org.http4s.{Request, Response, Status}
import upickle.default.Writer

/** 路由处理函数的便捷包装。
  *
  * 它把 bearer token 解析、统一异常处理和文本/JSON 响应创建集中到一个小对象上，减少各路由重复样板代码。
  */
final case class RouteSupport(
    routeContext: RouteContext
)

object RouteSupport:

  def fromRouteContext(routeContext: RouteContext): RouteSupport =
    RouteSupport(routeContext = routeContext)

  def bearerToken(support: RouteSupport, request: Request[IO]): Option[String] =
    HttpRequest.bearerToken(request)

  def handled(support: RouteSupport)(io: => IO[Response[IO]]): IO[Response[IO]] =
    HttpResponse.handled(support.routeContext)(io)

  def textResponse(
      support: RouteSupport,
      status: Status,
      payload: String,
      contentType: String
  ): IO[Response[IO]] =
    HttpResponse.textResponse(support.routeContext, status, payload, contentType)

  def jsonResponse[T: Writer](
      support: RouteSupport,
      status: Status,
      payload: T
  ): IO[Response[IO]] =
    HttpResponse.jsonResponse(support.routeContext, status, payload)
