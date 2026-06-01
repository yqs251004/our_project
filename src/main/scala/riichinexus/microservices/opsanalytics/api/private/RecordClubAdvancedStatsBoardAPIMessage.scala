package riichinexus.microservices.opsanalytics.api.`private`

import cats.effect.IO
import java.time.Instant

import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.club.domain.Club
import riichinexus.microservices.opsanalytics.domain.functions.AdvancedStatsBoardFunctions
import riichinexus.microservices.opsanalytics.objects.{AdvancedStatsBoard, DashboardOwner}
import riichinexus.microservices.opsanalytics.tables.advancedstatsboard.AdvancedStatsBoardTable
import riichinexus.microservices.player.api.GetPlayerAPIMessage
import riichinexus.microservices.player.objects.PlayerStatus
import upickle.default.*

final case class RecordClubAdvancedStatsBoardAPIMessage(
    club: Club,
    at: Instant
) extends APIMessage[AdvancedStatsBoard] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[AdvancedStatsBoard] =
    for
      activeMemberIds <- IO.blocking {
        club.members.filter { playerId =>
          GetPlayerAPIMessage.findPlayer(context.connection, playerId).exists(_.status == PlayerStatus.Active)
        }
      }
      memberBoards <- activeMemberIds.foldLeft(IO.pure(Vector.empty[AdvancedStatsBoard])) { (previous, playerId) =>
        previous.flatMap(boards =>
          RecordPlayerAdvancedStatsBoardAPIMessage(playerId, at).plan(context).map(board => boards :+ board)
        )
      }
      existingVersion <- IO.blocking {
        AdvancedStatsBoardTable.findByOwner(context.connection, DashboardOwner.Club(club.id)).map(_.version).getOrElse(0)
      }
      saved <- IO.blocking {
        AdvancedStatsBoardTable.save(
          context.connection,
          AdvancedStatsBoardFunctions.buildClubBoard(club, memberBoards, at, existingVersion)
        )
      }
    yield saved
