package riichinexus.microservices.club.api
import riichinexus.microservices.club.domain.functions.ClubViewFunctions
import riichinexus.microservices.auth.objects.Permission
import riichinexus.microservices.auth.api.`private`.ResolveAccessPrincipalPrivateAPIMessage
import riichinexus.microservices.auth.api.`private`.ResolveSystemAccessPrincipalPrivateAPIMessage
import riichinexus.microservices.player.api.`private`.{RecordPlayerClubRemovalPrivateAPIMessage, ResolvePlayerPrivateAPIMessage}

import riichinexus.microservices.club.domain.clubmanagement.functions.ClubFunctions
import java.time.Instant
import java.util.NoSuchElementException

import cats.effect.IO
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.microservices.club.objects.clubmanagement.ClubId
import riichinexus.microservices.auth.objects.`private`.AccessPrincipalPrivateView
import riichinexus.microservices.club.domain.Club
import riichinexus.system.json.JsonCodecs.given
import riichinexus.microservices.club.domain.{ClubAuthorization, ClubProjectionRefresher}
import riichinexus.microservices.club.objects.rankprivilegemanagement.ClubPrivilegeCode
import riichinexus.microservices.club.objects.clubmanagement.ClubView
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
      command = RemoveClubMemberCommand(
        clubId = ClubId(clubId),
        playerId = PlayerId(playerId),
        actor = actor,
        occurredAt = occurredAt
      )
      club <- removeClubMember(context, command).map(_.getOrElse(throw NoSuchElementException("Resource not found")))
    yield ClubViewFunctions.clubView(club)

  private def resolveOperatorActor(context: ApiPlanContext): IO[AccessPrincipalPrivateView] =
    operatorId.filter(_.nonEmpty)
      .map(id => ResolveAccessPrincipalPrivateAPIMessage(PlayerId(id)).plan(context))
      .getOrElse(ResolveSystemAccessPrincipalPrivateAPIMessage().plan(context))

  private def removeClubMember(
      context: ApiPlanContext,
      command: RemoveClubMemberCommand
  ): IO[Option[Club]] =
    val connection = context.connection
    for
      club <- IO.blocking(riichinexus.microservices.club.tables.clubs.ClubTable.findById(connection, command.clubId))
      _ <- ResolvePlayerPrivateAPIMessage(command.playerId).plan(context)
        .map(_.getOrElse(throw NoSuchElementException(s"Player ${command.playerId.value} was not found")))
      savedClub <- club match
        case None => IO.pure(None)
        case Some(club) =>
          ClubAuthorization.ensureClubActive(club)
          ClubAuthorization.requireClubMember(club, command.playerId, "remove member")
          ClubAuthorization.requireClubCapability(actor = command.actor,
            club = club,
            permission = Permission.ManageClubMembership,
            delegatedPrivileges = Set(ClubPrivilegeCode.ApproveRoster)
          )
          ensureMemberCanBeRemoved(club, command.clubId, command.playerId)
          for
            _ <- RecordPlayerClubRemovalPrivateAPIMessage(command.playerId, command.clubId).plan(context)
            refreshedClub <- ClubProjectionRefresher.refreshClubProjection(
              context,
              ClubFunctions.removeMember(club, command.playerId),
              command.occurredAt
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

  private final case class RemoveClubMemberCommand(
      clubId: ClubId,
      playerId: PlayerId,
      actor: AccessPrincipalPrivateView,
      occurredAt: Instant
  )
