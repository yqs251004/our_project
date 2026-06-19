package riichinexus.microservices.club.api

import riichinexus.microservices.club.domain.clubmanagement.functions.ClubFunctions
import java.util.NoSuchElementException

import cats.effect.IO
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.microservices.club.objects.clubmanagement.ClubId

import riichinexus.microservices.club.domain.rankprivilegemanagement.model.ClubMemberPrivilegeSnapshot

import riichinexus.microservices.club.objects.rankprivilegemanagement.apiTypes.ClubMemberPrivilegeSnapshotView
import riichinexus.microservices.club.tables.clubs.ClubTable
import upickle.default.ReadWriter

/** 获取俱乐部成员权限。 */
final case class GetClubMemberPrivilegeAPIMessage(
    clubId: String,
    playerId: String
) extends APIMessage[ClubMemberPrivilegeSnapshotView] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[ClubMemberPrivilegeSnapshotView] =
    for
      input <- IO.pure(GetClubMemberPrivilegeInput(ClubId(clubId), PlayerId(playerId)))
      snapshot <- IO.blocking(resolveSnapshot(context, input))
    yield ClubMemberPrivilegeSnapshotView.fromDomain(snapshot)

  private def resolveSnapshot(
      context: ApiPlanContext,
      input: GetClubMemberPrivilegeInput
  ): ClubMemberPrivilegeSnapshot =
    ClubTable.findById(context.connection, input.clubId)
      .flatMap { club =>
        if club.dissolvedAt.nonEmpty then
          throw IllegalArgumentException(s"Club ${club.id.value} has already been dissolved")
        ClubFunctions.memberPrivilegeSnapshot(club, input.playerId)
      }
      .getOrElse(throw NoSuchElementException("Resource not found"))

  private final case class GetClubMemberPrivilegeInput(
      clubId: ClubId,
      playerId: PlayerId
  )
