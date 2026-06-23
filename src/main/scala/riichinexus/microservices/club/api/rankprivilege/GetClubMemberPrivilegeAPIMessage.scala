package riichinexus.microservices.club.api.rankprivilege
import riichinexus.microservices.club.domain.profile.functions.ClubViewFunctions
import riichinexus.microservices.club.domain.profile.functions.ClubFunctions
import java.util.NoSuchElementException

import cats.effect.IO
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.microservices.player.objects.PlayerId
import riichinexus.microservices.club.objects.profile.ClubId

import riichinexus.microservices.club.domain.rankprivilege.model.ClubMemberPrivilegeSnapshot

import riichinexus.microservices.club.objects.rankprivilege.ClubMemberPrivilegeSnapshotView
import riichinexus.microservices.club.tables.clubs.ClubTable
/** 获取俱乐部成员权限。 */
final case class GetClubMemberPrivilegeAPIMessage(
    clubId: String,
    playerId: String
) extends APIMessage[ClubMemberPrivilegeSnapshotView]:

  override def plan(context: ApiPlanContext): IO[ClubMemberPrivilegeSnapshotView] =
    val requestedClubId = ClubId(clubId)
    val requestedPlayerId = PlayerId(playerId)
    for
      snapshot <- IO.blocking(resolveSnapshot(context, requestedClubId, requestedPlayerId))
    yield ClubViewFunctions.memberPrivilegeSnapshotView(snapshot)

  private def resolveSnapshot(
      context: ApiPlanContext,
      clubId: ClubId,
      playerId: PlayerId
  ): ClubMemberPrivilegeSnapshot =
    ClubTable.findById(context.connection, clubId)
      .flatMap { club =>
        if club.dissolvedAt.nonEmpty then
          throw IllegalArgumentException(s"Club ${club.id.value} has already been dissolved")
        ClubFunctions.memberPrivilegeSnapshot(club, playerId)
      }
      .getOrElse(throw NoSuchElementException("Resource not found"))
