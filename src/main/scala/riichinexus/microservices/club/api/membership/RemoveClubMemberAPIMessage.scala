package riichinexus.microservices.club.api.membership
import riichinexus.microservices.club.domain.profile.functions.ClubViewFunctions
import riichinexus.microservices.auth.objects.authorization.Permission
import riichinexus.microservices.auth.api.authorization.`private`.ResolveAccessPrincipalPrivateAPIMessage
import riichinexus.microservices.auth.api.authorization.`private`.ResolveSystemAccessPrincipalPrivateAPIMessage
import riichinexus.microservices.player.api.`private`.RecordPlayerClubRemovalPrivateAPIMessage
import riichinexus.microservices.player.api.`private`.ResolvePlayerPrivateAPIMessage

import riichinexus.microservices.club.domain.profile.functions.ClubFunctions
import java.time.Instant
import java.util.NoSuchElementException

import cats.effect.IO
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.microservices.player.objects.PlayerId
import riichinexus.microservices.club.objects.profile.ClubId
import riichinexus.microservices.auth.objects.authorization.`private`.AccessPrincipalPrivateView
import riichinexus.microservices.club.domain.profile.model.Club
import riichinexus.system.json.JsonCodecs.given
import riichinexus.microservices.club.domain.profile.functions.ClubAuthorization
import riichinexus.microservices.club.domain.profile.functions.ClubProjectionRefresher
import riichinexus.microservices.club.objects.rankprivilege.ClubPrivilegeCode
import riichinexus.microservices.club.objects.profile.ClubView
/** 从俱乐部移除成员。 */
final case class RemoveClubMemberAPIMessage(
    clubId: String,
    playerId: String,
    operatorId: Option[String] = None
) extends APIMessage[ClubView]:

  override def plan(context: ApiPlanContext): IO[ClubView] =
    for
      actor <- resolveOperatorActor(context)
      occurredAt <- IO.realTimeInstant
      requestedClubId = ClubId(clubId)
      requestedPlayerId = PlayerId(playerId)
      club <- removeClubMember(context, requestedClubId, requestedPlayerId, actor, occurredAt).map(_.getOrElse(throw NoSuchElementException("Resource not found")))
    yield ClubViewFunctions.clubView(club)

  private def resolveOperatorActor(context: ApiPlanContext): IO[AccessPrincipalPrivateView] =
    operatorId.filter(_.nonEmpty)
      .map(id => ResolveAccessPrincipalPrivateAPIMessage(PlayerId(id)).plan(context))
      .getOrElse(ResolveSystemAccessPrincipalPrivateAPIMessage().plan(context))

  private def removeClubMember(
      context: ApiPlanContext,
      clubId: ClubId,
      playerId: PlayerId,
      actor: AccessPrincipalPrivateView,
      occurredAt: Instant
  ): IO[Option[Club]] =
    val connection = context.connection
    for
      club <- IO.blocking(riichinexus.microservices.club.tables.clubs.ClubTable.findById(connection, clubId))
      _ <- ResolvePlayerPrivateAPIMessage(playerId).plan(context)
        .map(_.getOrElse(throw NoSuchElementException(s"Player ${playerId.value} was not found")))
      savedClub <- club match
        case None => IO.pure(None)
        case Some(club) =>
          ClubAuthorization.ensureClubActive(club)
          ClubAuthorization.requireClubMember(club, playerId, "remove member")
          ClubAuthorization.requireClubCapability(actor = actor,
            club = club,
            permission = Permission.ManageClubMembership,
            delegatedPrivileges = Set(ClubPrivilegeCode.ApproveRoster)
          )
          ensureMemberCanBeRemoved(club, clubId, playerId)
          for
            _ <- RecordPlayerClubRemovalPrivateAPIMessage(playerId, clubId).plan(context)
            refreshedClub <- ClubProjectionRefresher.refreshClubProjection(
              context,
              ClubFunctions.removeMember(club, playerId),
              occurredAt
            )
            savedClub <- IO.blocking(riichinexus.microservices.club.tables.clubs.ClubTable.save(connection, refreshedClub))
          yield Some(savedClub)
    yield savedClub

  private def ensureMemberCanBeRemoved(club: Club, clubId: ClubId, playerId: PlayerId): Unit =
    if club.creator == playerId then
      throw IllegalArgumentException(
        s"Club creator ${playerId.value} cannot be removed from active club ${clubId.value}"
      )

    if club.admins.contains(playerId) && club.admins.size <= 1 then
      throw IllegalArgumentException(
        s"Club ${clubId.value} must retain at least one club admin before removing ${playerId.value}"
      )
