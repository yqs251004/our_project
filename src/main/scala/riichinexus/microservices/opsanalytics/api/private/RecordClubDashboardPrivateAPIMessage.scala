package riichinexus.microservices.opsanalytics.api.`private`
import riichinexus.microservices.player.api.`private`.ResolvePlayerPrivateAPIMessage

import cats.effect.IO
import java.time.Instant

import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.microservices.club.api.`private`.ResolveClubReadModelsPrivateAPIMessage
import riichinexus.microservices.club.objects.clubmanagement.ClubId
import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.system.json.JsonCodecs.given
import riichinexus.microservices.opsanalytics.domain.functions.DashboardFunctions
import riichinexus.microservices.opsanalytics.objects.{Dashboard, DashboardOwner}
import riichinexus.microservices.opsanalytics.tables.dashboard.DashboardTable
import riichinexus.microservices.player.objects.PlayerStatus
import upickle.default.ReadWriter

/** 供后端服务计算并记录俱乐部仪表盘读模型。 */
final case class RecordClubDashboardPrivateAPIMessage(
    clubId: ClubId,
    at: Instant
) extends APIMessage[Dashboard] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[Dashboard] =
    for
      club <- ResolveClubReadModelsPrivateAPIMessage(Vector(clubId)).plan(context)
        .map(_.headOption.getOrElse(throw java.util.NoSuchElementException(s"Club ${clubId.value} was not found")))
      activeMemberIds <- resolveActiveMemberIds(context, club.members)
      memberDashboards <- loadMemberDashboards(context, activeMemberIds)
      existingVersion <- loadExistingVersion(context)
      dashboard <- saveClubDashboard(context, club.id, memberDashboards, existingVersion)
    yield dashboard

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

  private def loadMemberDashboards(
      context: ApiPlanContext,
      memberIds: Vector[PlayerId]
  ): IO[Vector[Dashboard]] =
    IO.blocking {
      memberIds.flatMap(playerId => DashboardTable.findByOwner(context.connection, DashboardOwner.Player(playerId)))
    }

  private def loadExistingVersion(context: ApiPlanContext): IO[Int] =
    IO.blocking {
      DashboardTable.findByOwner(context.connection, DashboardOwner.Club(clubId)).map(_.version).getOrElse(0)
    }

  private def saveClubDashboard(
      context: ApiPlanContext,
      clubId: ClubId,
      memberDashboards: Vector[Dashboard],
      existingVersion: Int
  ): IO[Dashboard] =
    IO.blocking {
      DashboardTable.save(
        context.connection,
        DashboardFunctions.buildClubDashboard(clubId, memberDashboards, at, existingVersion)
      )
    }
