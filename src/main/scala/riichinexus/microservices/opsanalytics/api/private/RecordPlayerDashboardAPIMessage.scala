package riichinexus.microservices.opsanalytics.api.`private`

import cats.effect.IO
import java.time.Instant

import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.domain.model.PlayerId
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.opsanalytics.domain.functions.DashboardFunctions
import riichinexus.microservices.opsanalytics.objects.{Dashboard, DashboardOwner}
import riichinexus.microservices.opsanalytics.tables.dashboard.DashboardTable
import riichinexus.microservices.tournament.api.`private`.{
  ListPlayerMatchRecordsPrivateAPIMessage,
  ListPlayerPaifusPrivateAPIMessage
}
import upickle.default.*

final case class RecordPlayerDashboardAPIMessage(
    playerId: PlayerId,
    at: Instant
) extends APIMessage[Dashboard] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[Dashboard] =
    for
      records <- ListPlayerMatchRecordsPrivateAPIMessage(playerId).plan(context)
      paifus <- ListPlayerPaifusPrivateAPIMessage(playerId).plan(context)
      existingVersion <- IO.blocking {
        DashboardTable.findByOwner(context.connection, DashboardOwner.Player(playerId)).map(_.version).getOrElse(0)
      }
      saved <- IO.blocking {
        DashboardTable.save(
          context.connection,
          DashboardFunctions.buildPlayerDashboard(playerId, records, paifus.flatMap(_.rounds), at, existingVersion)
        )
      }
    yield saved
