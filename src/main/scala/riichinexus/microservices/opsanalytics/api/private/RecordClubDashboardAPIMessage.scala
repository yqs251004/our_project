package riichinexus.microservices.opsanalytics.api.`private`

import cats.effect.IO
import java.time.Instant

import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.domain.model.PlayerId
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.club.domain.Club
import riichinexus.microservices.opsanalytics.domain.functions.DashboardFunctions
import riichinexus.microservices.opsanalytics.objects.{Dashboard, DashboardOwner}
import riichinexus.microservices.opsanalytics.tables.dashboard.DashboardTable
import riichinexus.microservices.player.api.GetPlayerAPIMessage
import riichinexus.microservices.player.objects.PlayerStatus
import upickle.default.*

final case class RecordClubDashboardAPIMessage(
    club: Club,
    at: Instant
) extends APIMessage[Dashboard] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[Dashboard] =
    for
      memberDashboards <- IO.blocking {
        club.members.flatMap { playerId =>
          GetPlayerAPIMessage.findPlayer(context.connection, playerId)
            .filter(_.status == PlayerStatus.Active)
            .flatMap(_ => DashboardTable.findByOwner(context.connection, DashboardOwner.Player(playerId)))
        }
      }
      existingVersion <- IO.blocking {
        DashboardTable.findByOwner(context.connection, DashboardOwner.Club(club.id)).map(_.version).getOrElse(0)
      }
      dashboard <- IO.blocking {
        DashboardTable.save(
          context.connection,
          DashboardFunctions.buildClubDashboard(club, memberDashboards, at, existingVersion)
        )
      }
    yield dashboard
