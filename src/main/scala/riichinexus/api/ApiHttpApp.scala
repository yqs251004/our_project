package riichinexus.api

import cats.data.Kleisli
import cats.effect.IO
import org.http4s.HttpApp
import org.http4s.Request
import org.http4s.server.middleware.Logger
import riichinexus.api.http.ApiRouter

object ApiHttpApp:

  private val normalizedPathPrefixes = Vector(
    "player-" -> ":playerId",
    "club-" -> ":clubId",
    "tournament-" -> ":tournamentId",
    "stage-" -> ":stageId",
    "table-" -> ":tableId",
    "paifu-" -> ":paifuId",
    "record-" -> ":recordId",
    "appeal-" -> ":appealId",
    "membership-" -> ":membershipApplicationId",
    "lineup-" -> ":lineupSubmissionId",
    "guest-" -> ":guestSessionId",
    "settlement-" -> ":settlementId",
    "audit-" -> ":auditEventId",
    "advanced-stats-task-" -> ":advancedStatsTaskId",
    "event-cascade-" -> ":eventCascadeRecordId",
    "event-outbox-" -> ":outboxRecordId",
    "event-delivery-" -> ":deliveryReceiptId",
    "event-cursor-" -> ":subscriberCursorId"
  )

  private def normalizePath(rawPath: String): String =
    val segments = rawPath.split("/").toVector.filter(_.nonEmpty).map { segment =>
      normalizedPathPrefixes.collectFirst {
        case (prefix, placeholder) if segment.startsWith(prefix) => placeholder
      }.orElse {
        Option.when(segment.nonEmpty && segment.forall(_.isDigit))(":id")
      }.getOrElse(segment)
    }
    if segments.isEmpty then "/" else s"/${segments.mkString("/")}"

  private def instrumentRequests(
      runtime: ApiRuntimeContext,
      httpApp: HttpApp[IO]
  ): HttpApp[IO] =
    Kleisli { (request: Request[IO]) =>
      IO.monotonic.flatMap { startedAt =>
        httpApp(request).attempt.flatMap {
          case Right(response) =>
            IO.monotonic.flatMap { finishedAt =>
              IO(runtime.performanceDiagnosticsService.recordRequest(
                method = request.method.name,
                path = normalizePath(request.uri.path.renderString),
                statusCode = response.status.code,
                durationNanos = (finishedAt - startedAt).toNanos
              )).as(response)
            }
          case Left(error) =>
            IO.monotonic.flatMap { finishedAt =>
              IO(runtime.performanceDiagnosticsService.recordRequest(
                method = request.method.name,
                path = normalizePath(request.uri.path.renderString),
                statusCode = 500,
                durationNanos = (finishedAt - startedAt).toNanos
              )) *> IO.raiseError(error)
            }
        }
      }
    }

  def build(
      runtime: ApiRuntimeContext,
      logHeaders: Boolean = true,
      logBody: Boolean = false
  ): HttpApp[IO] =
    val routed = ApiRouter.httpApp(runtime.routeContext)
    Logger.httpApp(logHeaders = logHeaders, logBody = logBody)(
      instrumentRequests(runtime, routed)
    )
