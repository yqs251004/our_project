package riichinexus.microservices.club.api
import riichinexus.microservices.auth.objects.Permission
import riichinexus.microservices.auth.utils.{ResolveAccessPrincipal, ResolveGuestAccessPrincipal, ResolveRequestActor}
import riichinexus.microservices.auth.api.AuthCheckPermissionAPIMessage
import riichinexus.microservices.player.api.`private`.*

import riichinexus.microservices.auth.domain.functions.{AccessPrincipalFunctions, AuthorizationPolicyFunctions, RoleGrantFunctions}

import riichinexus.microservices.club.domain.clubmanagement.functions.ClubFunctions
import java.util.NoSuchElementException

import cats.effect.IO
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.microservices.player.domain.functions.PlayerIdGenerator
import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.microservices.club.domain.functions.ClubIdGenerator
import riichinexus.microservices.club.objects.clubmanagement.ClubId
import riichinexus.microservices.club.objects.membershipmanagement.MembershipApplicationId
import riichinexus.microservices.tournament.domain.functions.TournamentIdGenerator
import riichinexus.microservices.tournament.objects.lineupmanagement.LineupSubmissionId
import riichinexus.microservices.tournament.objects.paifumanagement.PaifuId
import riichinexus.microservices.tournament.objects.recordmanagement.MatchRecordId
import riichinexus.microservices.tournament.objects.settlementmanagement.SettlementSnapshotId
import riichinexus.microservices.tournament.objects.tablemanagement.TableId
import riichinexus.microservices.tournament.objects.tournamentmanagement.{TournamentId, TournamentStageId}
import riichinexus.microservices.tournament.appeal.domain.functions.AppealIdGenerator
import riichinexus.microservices.tournament.appeal.objects.ticketmanagement.AppealTicketId
import riichinexus.microservices.auth.domain.functions.AuthIdGenerator
import riichinexus.microservices.auth.objects.sessionmanagement.GuestSessionId
import riichinexus.microservices.audit.domain.functions.AuditIdGenerator
import riichinexus.microservices.audit.domain.auditevent.AuditEventId
import riichinexus.microservices.opsanalytics.domain.functions.OpsAnalyticsIdGenerator
import riichinexus.microservices.opsanalytics.objects.advancedstats.AdvancedStatsRecomputeTaskId
import riichinexus.microservices.auth.domain.model.*
import riichinexus.microservices.club.domain.Club
import riichinexus.microservices.club.domain.clubmanagement.model.*
import riichinexus.microservices.club.domain.membershipmanagement.model.*
import riichinexus.microservices.club.domain.rankprivilegemanagement.model.*
import riichinexus.microservices.club.domain.relationmanagement.model.*
import riichinexus.microservices.player.domain.Player
import riichinexus.microservices.player.objects.*
import riichinexus.microservices.player.domain.functions.PlayerRoleFunctions
import riichinexus.microservices.auth.domain.*
import riichinexus.system.json.JsonCodecs.given
import riichinexus.microservices.club.domain.ClubAuthorization
import riichinexus.microservices.club.objects.clubmanagement.ClubView
import riichinexus.microservices.player.api.{CreatePlayerAPIMessage, GetPlayerAPIMessage, ListPlayersAPIMessage}
import upickle.default.*

final case class RevokeClubAdminAPIMessage(
    clubId: String,
    playerId: String,
    operatorId: Option[String] = None
) extends APIMessage[ClubView] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[ClubView] =
    for
      actor <- resolveOperatorActor(context)
      command = RevokeClubAdminCommand(
        clubId = ClubId(clubId),
        playerId = PlayerId(playerId),
        actor = actor
      )
      club <- revokeAdmin(context, command).map(_.getOrElse(throw NoSuchElementException("Resource not found")))
    yield ClubView.fromDomain(club)

  private def resolveOperatorActor(context: ApiPlanContext): IO[AccessPrincipal] =
    operatorId.filter(_.nonEmpty)
      .map(id => ResolveAccessPrincipal(PlayerId(id)).plan(context))
      .getOrElse(IO.pure(AccessPrincipalFunctions.system))

  private def revokeAdmin(
      context: ApiPlanContext,
      command: RevokeClubAdminCommand
  ): IO[Option[Club]] =
    val connection = context.connection
    for
      club <- IO.blocking(riichinexus.microservices.club.tables.clubs.ClubTable.findById(connection, command.clubId))
      player <- ResolvePlayerPrivateAPIMessage(command.playerId).plan(context)
        .map(_.getOrElse(throw NoSuchElementException(s"Player ${command.playerId.value} was not found")))
      savedClub <- club match
        case None => IO.pure(None)
        case Some(club) =>
          ensureAdminCanBeRevoked(club, command)
          for
            _ <- SavePlayerPrivateAPIMessage(PlayerRoleFunctions.revokeClubAdmin(player, command.clubId)).plan(context)
            savedClub <- IO.blocking(riichinexus.microservices.club.tables.clubs.ClubTable.save(connection, ClubFunctions.revokeAdmin(club, command.playerId)))
          yield Some(savedClub)
    yield savedClub

  private def ensureAdminCanBeRevoked(
      club: Club,
      command: RevokeClubAdminCommand
  ): Unit =
    ClubAuthorization.ensureClubActive(club)
    ClubAuthorization.requireClubMember(club, command.playerId, "revoke club admin")
    ClubAuthorization.requireClubAdmin(actor = command.actor,
      club = club,
      permission = Permission.AssignClubAdmin
    )
    ensureTargetIsAdmin(club, command)
    ensureAnotherAdminRemains(club, command)

  private def ensureTargetIsAdmin(club: Club, command: RevokeClubAdminCommand): Unit =
    if !club.admins.contains(command.playerId) then
      throw IllegalArgumentException(
        s"Player ${command.playerId.value} is not a club admin of club ${command.clubId.value}"
      )

  private def ensureAnotherAdminRemains(club: Club, command: RevokeClubAdminCommand): Unit =
    if club.admins.size <= 1 then
      throw IllegalArgumentException(
        s"Club ${command.clubId.value} must retain at least one club admin"
      )

  private final case class RevokeClubAdminCommand(
      clubId: ClubId,
      playerId: PlayerId,
      actor: AccessPrincipal
  )

