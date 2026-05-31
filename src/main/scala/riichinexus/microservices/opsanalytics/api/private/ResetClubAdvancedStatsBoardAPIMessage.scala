package riichinexus.microservices.opsanalytics.api.`private`

import cats.effect.IO
import java.time.Instant

import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.domain.model.ClubId
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.opsanalytics.objects.{AdvancedStatsBoard, DashboardOwner}
import riichinexus.microservices.opsanalytics.tables.advancedstatsboard.AdvancedStatsBoardTable
import upickle.default.*

final case class ResetClubAdvancedStatsBoardAPIMessage(
    clubId: ClubId,
    at: Instant
) extends APIMessage[AdvancedStatsBoard] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[AdvancedStatsBoard] =
    for
      board <- IO.blocking {
        val owner = DashboardOwner.Club(clubId)
        AdvancedStatsBoardTable.save(
          context.connection,
          AdvancedStatsBoard.empty(owner, at).copy(
            version = AdvancedStatsBoardTable.findByOwner(context.connection, owner).map(_.version).getOrElse(0)
          )
        )
      }
    yield board
