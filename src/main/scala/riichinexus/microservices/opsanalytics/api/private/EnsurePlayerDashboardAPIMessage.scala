package riichinexus.microservices.opsanalytics.api.`private`

import cats.effect.IO
import java.time.Instant

import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.domain.model.PlayerId
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.opsanalytics.domain.functions.DashboardFunctions
import riichinexus.microservices.opsanalytics.objects.{Dashboard, DashboardOwner}
import riichinexus.microservices.opsanalytics.tables.dashboard.DashboardTable
import upickle.default.*

final case class EnsurePlayerDashboardAPIMessage(
    playerId: PlayerId,
    at: Instant
) extends APIMessage[Dashboard] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[Dashboard] =
    for
      dashboard <- IO.blocking {
        val owner = DashboardOwner.Player(playerId)
        DashboardTable.findByOwner(context.connection, owner)
          .getOrElse(DashboardTable.save(context.connection, DashboardFunctions.empty(owner, at)))
      }
    yield dashboard
