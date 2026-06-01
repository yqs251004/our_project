package riichinexus.microservices.opsanalytics.api.`private`

import cats.effect.IO
import java.time.Instant

import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.domain.model.PlayerId
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.opsanalytics.domain.functions.AdvancedStatsBoardFunctions
import riichinexus.microservices.opsanalytics.objects.{AdvancedStatsBoard, DashboardOwner}
import riichinexus.microservices.opsanalytics.tables.advancedstatsboard.AdvancedStatsBoardTable
import riichinexus.microservices.tournament.api.`private`.{
  ListPlayerMatchRecordsPrivateAPIMessage,
  ListPlayerPaifusPrivateAPIMessage
}
import upickle.default.*

final case class RecordPlayerAdvancedStatsBoardAPIMessage(
    playerId: PlayerId,
    at: Instant
) extends APIMessage[AdvancedStatsBoard] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[AdvancedStatsBoard] =
    for
      records <- ListPlayerMatchRecordsPrivateAPIMessage(playerId).plan(context)
      paifus <- ListPlayerPaifusPrivateAPIMessage(playerId).plan(context)
      existingVersion <- IO.blocking {
        AdvancedStatsBoardTable.findByOwner(context.connection, DashboardOwner.Player(playerId)).map(_.version).getOrElse(0)
      }
      saved <- IO.blocking {
        AdvancedStatsBoardTable.save(
          context.connection,
          AdvancedStatsBoardFunctions.buildPlayerBoard(playerId, records, paifus, at, existingVersion)
        )
      }
    yield saved
