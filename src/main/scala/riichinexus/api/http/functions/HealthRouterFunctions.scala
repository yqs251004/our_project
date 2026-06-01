package riichinexus.api.http.functions

import java.time.Instant

import cats.effect.IO
import io.circe.generic.auto.*
import io.circe.syntax.*
import org.http4s.HttpRoutes
import org.http4s.circe.CirceEntityEncoder.*
import org.http4s.dsl.io.*
import org.typelevel.log4cats.slf4j.Slf4jLogger
import riichinexus.system.objects.HealthResponse

object HealthRouterFunctions:

  private val logger = Slf4jLogger.getLogger[IO]

  def routes(storageLabel: String): HttpRoutes[IO] = HttpRoutes.of[IO] {
    case GET -> Root =>
      for
        _ <- logger.info("HealthRouter received GET /")
        response <- healthResponse(storageLabel)
      yield response

    case GET -> Root / "health" =>
      for
        _ <- logger.info("HealthRouter received GET /health")
        response <- healthResponse(storageLabel)
      yield response

    case GET -> Root / "api" / "health" =>
      for
        _ <- logger.info("HealthRouter received GET /api/health")
        response <- healthResponse(storageLabel)
      yield response
  }

  private def healthResponse(storageLabel: String) =
    Ok(
      HealthResponse(
        status = "ok",
        storage = storageLabel,
        timestamp = Instant.now(),
        service = "riichi-nexus"
      ).asJson
    )
