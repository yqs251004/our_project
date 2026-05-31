package riichinexus.microservices.opsanalytics.api.`private`

import cats.effect.IO
import java.time.Instant

import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.domain.model.ClubId
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.opsanalytics.objects.{Dashboard, DashboardOwner}
import riichinexus.microservices.opsanalytics.tables.dashboard.DashboardTable
import upickle.default.*

final case class ResetClubDashboardAPIMessage(
    clubId: ClubId,
    at: Instant
) extends APIMessage[Dashboard] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[Dashboard] =
    for
      dashboard <- IO.blocking {
        val owner = DashboardOwner.Club(clubId)
        DashboardTable.save(
          context.connection,
          Dashboard.empty(owner, at).copy(
            version = DashboardTable.findByOwner(context.connection, owner).map(_.version).getOrElse(0)
          )
        )
      }
    yield dashboard
