package riichinexus.microservices.club.api

import riichinexus.microservices.club.domain.clubmanagement.functions.ClubFunctions
import java.util.NoSuchElementException

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.domain.model.*
import riichinexus.microservices.club.domain.Club
import riichinexus.microservices.club.domain.clubmanagement.model.*
import riichinexus.microservices.club.domain.membershipmanagement.model.*
import riichinexus.microservices.club.domain.rankprivilegemanagement.model.*
import riichinexus.microservices.club.domain.relationmanagement.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.club.objects.rankprivilegemanagement.apiTypes.ClubMemberPrivilegeSnapshotView
import riichinexus.microservices.club.tables.clubs.ClubTable
import upickle.default.*

final case class GetClubMemberPrivilegeAPIMessage(
    clubId: String,
    playerId: String
) extends APIMessage[ClubMemberPrivilegeSnapshotView] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[ClubMemberPrivilegeSnapshotView] =
    for
      input <- IO.blocking(GetClubMemberPrivilegeInput(ClubId(clubId), PlayerId(playerId)))
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
