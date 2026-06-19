package riichinexus.system.api.http

import java.time.Instant

import cats.effect.IO
import org.http4s.{HttpRoutes, Method, Request, Response, Status}
import org.typelevel.log4cats.slf4j.Slf4jLogger
import riichinexus.system.objects.HealthResponse
import upickle.default.write

object HealthRouter:

  private val logger = Slf4jLogger.getLogger[IO]

  def routes(storageLabel: String): HttpRoutes[IO] = HttpRoutes.of[IO] {
    case request if request.method == Method.GET && normalizedPath(request) == "/" =>
      for
        _ <- logger.info("HealthRouter received GET /")
        response <- healthResponse(storageLabel)
      yield response

    case request if request.method == Method.GET && normalizedPath(request) == "/health" =>
      for
        _ <- logger.info("HealthRouter received GET /health")
        response <- healthResponse(storageLabel)
      yield response

    case request if request.method == Method.GET && normalizedPath(request) == "/api/health" =>
      for
        _ <- logger.info("HealthRouter received GET /api/health")
        response <- healthResponse(storageLabel)
      yield response
  }

  private def healthResponse(storageLabel: String): IO[Response[IO]] =
    IO.pure(
      Response[IO](status = Status.Ok).withEntity(
        write(
          HealthResponse(
            status = "ok",
            storage = storageLabel,
            timestamp = Instant.now(),
            service = "riichi-nexus"
          )
        )
      )
    )

  private def normalizedPath(request: Request[IO]): String =
    val rendered = request.uri.path.renderString
    if rendered.isEmpty then "/" else rendered
