package riichinexus.microservices.club.api
import riichinexus.microservices.auth.utils.{ResolveAccessPrincipal, ResolveGuestAccessPrincipal, ResolveRequestActor}
import riichinexus.microservices.auth.api.AuthCheckPermissionAPIMessage
import riichinexus.microservices.player.api.`private`.*

import riichinexus.microservices.auth.domain.functions.{AccessPrincipalFunctions, AuthorizationPolicyFunctions, RoleGrantFunctions}

import riichinexus.microservices.club.domain.clubmanagement.functions.ClubFunctions
import java.time.Instant
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
import riichinexus.microservices.player.domain.functions.{PlayerClubBindingFunctions, PlayerRoleFunctions}
import riichinexus.microservices.auth.domain.*
import riichinexus.system.json.JsonCodecs.given
import riichinexus.microservices.club.domain.{ClubAuthorization, ClubProjectionRefresher}
import riichinexus.microservices.club.objects.clubmanagement.ClubView
import riichinexus.microservices.player.api.{CreatePlayerAPIMessage, GetPlayerAPIMessage, ListPlayersAPIMessage}
import upickle.default.*

final case class CreateClubAPIMessage(
    name: String,
    creatorId: String
) extends APIMessage[ClubView] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[ClubView] =
    for
      parsedCreatorId <- IO.blocking(PlayerId(creatorId))
      actor <- ResolveAccessPrincipal(parsedCreatorId).plan(context)
      createdAt <- IO.realTimeInstant
      command = CreateClubCommand(
        name = name,
        creatorId = parsedCreatorId,
        actor = actor,
        createdAt = createdAt
      )
      club <- createClub(context, command)
    yield ClubView.fromDomain(club)

  private def createClub(context: ApiPlanContext, command: CreateClubCommand): IO[Club] =
    val connection = context.connection
    val normalizedName = command.name.trim
    require(normalizedName.nonEmpty, "Club name cannot be empty")

    for
      creator <- ResolvePlayerPrivateAPIMessage(command.creatorId).plan(context)
        .map(_.getOrElse(throw NoSuchElementException(s"Player ${command.creatorId.value} was not found")))
      _ <- IO.blocking {
        requireActivePlayer(creator, s"Player ${command.creatorId.value} cannot create a club")
        ensureCreatorCanCreateClub(command.actor, command.creatorId)
      }
      club <- IO.blocking(resolveClubToCreate(connection, normalizedName, command.creatorId, command.createdAt))
      updatedCreator = PlayerRoleFunctions.grantRole(
        PlayerClubBindingFunctions.joinClub(creator, club.id),
        RoleGrantFunctions.clubAdmin(club.id, command.createdAt, command.actor.playerId)
      )
      savedCreator <- SavePlayerPrivateAPIMessage(updatedCreator).plan(context)
      _ <- ClubProjectionRefresher.ensurePlayerDashboard(context, savedCreator.id, command.createdAt)
      refreshedClub <- ClubProjectionRefresher.refreshClubProjection(context, club, command.createdAt)
      savedClub <- IO.blocking(riichinexus.microservices.club.tables.clubs.ClubTable.save(connection, refreshedClub))
    yield savedClub

  private def resolveClubToCreate(
      connection: java.sql.Connection,
      normalizedName: String,
      creatorId: PlayerId,
      createdAt: Instant
  ): Club =
    riichinexus.microservices.club.tables.clubs.ClubTable.findByName(connection, normalizedName) match
      case Some(existing) =>
        ClubAuthorization.ensureClubActive(existing)
        ClubFunctions.grantAdmin(
          ClubFunctions.addMember(existing, creatorId),
          creatorId
        )
      case None =>
        Club(
          id = ClubIdGenerator.clubId(),
          name = normalizedName,
          creator = creatorId,
          createdAt = createdAt,
          members = Vector(creatorId),
          admins = Vector(creatorId)
        )

  private def ensureCreatorCanCreateClub(actor: AccessPrincipal, creatorId: PlayerId): Unit =
    if !AccessPrincipalFunctions.isSuperAdmin(actor) && actor.playerId.exists(_ != creatorId) then
      throw AuthorizationFailure("Only the creator or a super admin can create the club")

  private def requireActivePlayer(player: Player, context: String): Unit =
    if player.status != PlayerStatus.Active then
      throw IllegalArgumentException(context)

  private final case class CreateClubCommand(
      name: String,
      creatorId: PlayerId,
      actor: AccessPrincipal,
      createdAt: Instant
  )
