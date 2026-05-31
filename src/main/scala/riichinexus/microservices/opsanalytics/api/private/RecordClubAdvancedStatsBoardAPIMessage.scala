package riichinexus.microservices.opsanalytics.api.`private`

import cats.effect.IO

import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.opsanalytics.objects.AdvancedStatsBoard
import riichinexus.microservices.opsanalytics.tables.advancedstatsboard.AdvancedStatsBoardTable
import upickle.default.*

final case class RecordClubAdvancedStatsBoardAPIMessage(
    board: AdvancedStatsBoard
) extends APIMessage[AdvancedStatsBoard] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[AdvancedStatsBoard] =
    for
      saved <- IO.blocking {
        AdvancedStatsBoardTable.save(
          context.connection,
          board.copy(
            version = AdvancedStatsBoardTable.findByOwner(context.connection, board.owner).map(_.version).getOrElse(0)
          )
        )
      }
    yield saved
