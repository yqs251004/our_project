package riichinexus.system.realtime.router

import scala.concurrent.duration.*

import cats.effect.IO
import fs2.Stream
import fs2.text
import org.http4s.{Header, Headers, HttpRoutes, Response, Status}
import org.http4s.dsl.io.*
import org.typelevel.ci.CIString
import riichinexus.system.realtime.objects.RealtimeEvent
import riichinexus.system.api.http.RouteContext
import upickle.default.write

object RealtimeRouter:

  def routes(routeContext: RouteContext): HttpRoutes[IO] =
    HttpRoutes.of[IO] {
      case GET -> Root / "api" / "realtime" / "stream" =>
        val eventStream =
          Stream.resource(routeContext.realtimeEventBus.subscribe).flatMap(identity)
        val heartbeat = Stream.awakeEvery[IO](20.seconds).as("event: ping\ndata: {}\n\n")
        val events = eventStream.map(event => s"data: ${write(event)}\n\n")

        IO.pure(sseResponse(routeContext, events.merge(heartbeat)))
    }

  private def sseResponse(routeContext: RouteContext, stream: Stream[IO, String]): Response[IO] =
    Response[IO](
      status = Status.Ok,
      headers = Headers(
        Header.Raw(CIString("Content-Type"), "text/event-stream; charset=utf-8"),
        Header.Raw(CIString("Cache-Control"), "no-cache"),
        Header.Raw(CIString("Connection"), "keep-alive"),
        Header.Raw(CIString("Access-Control-Allow-Origin"), routeContext.corsAllowOrigin),
        Header.Raw(CIString("Access-Control-Allow-Methods"), "GET, POST, OPTIONS"),
        Header.Raw(CIString("Access-Control-Allow-Headers"), "Content-Type, Authorization")
      ),
      body = stream.through(text.utf8.encode)
    )
