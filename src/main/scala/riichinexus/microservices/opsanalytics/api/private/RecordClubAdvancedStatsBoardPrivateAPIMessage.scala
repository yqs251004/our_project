package riichinexus.microservices.opsanalytics.api.`private`
import riichinexus.microservices.player.api.`private`.ResolvePlayerPrivateAPIMessage

import cats.effect.IO
import java.time.Instant

import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.microservices.club.api.`private`.ResolveClubReadModelsPrivateAPIMessage
import riichinexus.microservices.club.objects.clubmanagement.ClubId
import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.system.json.JsonCodecs.given
import riichinexus.microservices.opsanalytics.domain.functions.AdvancedStatsBoardFunctions
import riichinexus.microservices.opsanalytics.objects.{AdvancedStatsBoard, DashboardOwner}
import riichinexus.microservices.opsanalytics.tables.advancedstatsboard.AdvancedStatsBoardTable
import riichinexus.microservices.player.objects.PlayerStatus
import upickle.default.ReadWriter

/** 供后端服务计算并记录俱乐部高级统计读模型。 */
final case class RecordClubAdvancedStatsBoardPrivateAPIMessage(
    clubId: ClubId,
    at: Instant
) extends APIMessage[AdvancedStatsBoard] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[AdvancedStatsBoard] =
    for
      club <- ResolveClubReadModelsPrivateAPIMessage(Vector(clubId)).plan(context)
        .map(_.headOption.getOrElse(throw java.util.NoSuchElementException(s"Club ${clubId.value} was not found")))
      activeMemberIds <- resolveActiveMemberIds(context, club.members)
      memberBoards <- rebuildMemberBoards(context, activeMemberIds)
      existingVersion <- loadExistingVersion(context)
      saved <- saveClubBoard(context, club.id, memberBoards, existingVersion)
    yield saved

  private def resolveActiveMemberIds(
      context: ApiPlanContext,
      memberIds: Vector[PlayerId]
  ): IO[Vector[PlayerId]] =
    memberIds.foldLeft(IO.pure(Vector.empty[PlayerId])) { (previous, playerId) =>
      previous.flatMap { ids =>
        ResolvePlayerPrivateAPIMessage(playerId).plan(context).map {
          case Some(player) if player.status == PlayerStatus.Active => ids :+ playerId
          case _                                                    => ids
        }
      }
    }

  private def rebuildMemberBoards(
      context: ApiPlanContext,
      memberIds: Vector[PlayerId]
  ): IO[Vector[AdvancedStatsBoard]] =
    memberIds.foldLeft(IO.pure(Vector.empty[AdvancedStatsBoard])) { (previous, playerId) =>
      previous.flatMap(boards =>
        RecordPlayerAdvancedStatsBoardPrivateAPIMessage(playerId, at).plan(context).map(board => boards :+ board)
      )
    }

  private def loadExistingVersion(context: ApiPlanContext): IO[Int] =
    IO.blocking {
      AdvancedStatsBoardTable.findByOwner(context.connection, DashboardOwner.Club(clubId)).map(_.version).getOrElse(0)
    }

  private def saveClubBoard(
      context: ApiPlanContext,
      clubId: ClubId,
      memberBoards: Vector[AdvancedStatsBoard],
      existingVersion: Int
  ): IO[AdvancedStatsBoard] =
    IO.blocking {
      AdvancedStatsBoardTable.save(
        context.connection,
        AdvancedStatsBoardFunctions.buildClubBoard(clubId, memberBoards, at, existingVersion)
      )
    }
