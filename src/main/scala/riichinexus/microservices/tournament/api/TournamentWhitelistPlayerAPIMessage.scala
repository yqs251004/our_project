package riichinexus.microservices.tournament.api
import riichinexus.microservices.auth.objects.Permission
import riichinexus.microservices.auth.utils.{ResolveAccessPrincipal, ResolveGuestAccessPrincipal, ResolveRequestActor}
import riichinexus.microservices.auth.api.AuthCheckPermissionAPIMessage
import riichinexus.microservices.player.api.`private`.*

import riichinexus.microservices.auth.domain.functions.{AccessPrincipalFunctions, AuthorizationPolicyFunctions, RoleGrantFunctions}

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
import riichinexus.microservices.tournament.domain.tournamentmanagement.functions.TournamentFunctions
import riichinexus.microservices.tournament.domain.lineupmanagement.model.*
import riichinexus.microservices.tournament.domain.recordmanagement.model.*
import riichinexus.microservices.tournament.domain.settlementmanagement.model.*
import riichinexus.microservices.tournament.domain.tablemanagement.model.*
import riichinexus.microservices.tournament.domain.tournamentmanagement.model.*
import riichinexus.microservices.player.domain.Player
import riichinexus.microservices.player.objects.*
import riichinexus.system.json.JsonCodecs.given
import riichinexus.microservices.player.api.{CreatePlayerAPIMessage, GetPlayerAPIMessage, ListPlayersAPIMessage}
import riichinexus.microservices.tournament.objects.lineupmanagement.apiTypes.*
import riichinexus.microservices.tournament.objects.paifumanagement.apiTypes.*
import riichinexus.microservices.tournament.objects.recordmanagement.apiTypes.*
import riichinexus.microservices.tournament.objects.rulesmanagement.apiTypes.*
import riichinexus.microservices.tournament.objects.settlementmanagement.apiTypes.*
import riichinexus.microservices.tournament.objects.tablemanagement.apiTypes.*
import riichinexus.microservices.tournament.objects.tournamentmanagement.apiTypes.*
import riichinexus.microservices.tournament.objects.tournamentmanagement.apiTypes.AssignTournamentAdminRequest.given
import upickle.default.*

final case class TournamentWhitelistPlayerAPIMessage(tournamentId: String, playerId: String, operatorId: Option[String] = None) extends APIMessage[TournamentSummaryView] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[TournamentSummaryView] =
    for
      actor <- resolveOperatorActor(context)
      command = WhitelistPlayerCommand(TournamentId(tournamentId), PlayerId(playerId), actor)
      tournament <- whitelistPlayer(context, command).map(_.getOrElse(throw NoSuchElementException("Resource not found")))
    yield TournamentSummaryView.fromDomain(tournament)

  private def resolveOperatorActor(context: ApiPlanContext): IO[AccessPrincipal] =
    operatorId.map(PlayerId(_))
      .map(ResolveAccessPrincipal(_).plan(context))
      .getOrElse(IO.pure(AccessPrincipalFunctions.system))

  private def whitelistPlayer(
      context: ApiPlanContext,
      command: WhitelistPlayerCommand
  ): IO[Option[Tournament]] =
    val connection = context.connection
    for
      player <- ResolvePlayerPrivateAPIMessage(command.playerId).plan(context)
        .map(_.getOrElse(throw NoSuchElementException(s"Player ${command.playerId.value} was not found")))
      tournament <- IO.blocking {
        AuthorizationPolicyFunctions.requirePermission(AuthorizationPolicyFunctions.strict,
          command.actor,
          Permission.ManageTournamentStages,
          tournamentId = Some(command.tournamentId)
        )
        ensurePlayerCanBeWhitelisted(player, command.playerId)
        riichinexus.microservices.tournament.tables.tournaments.TournamentTable.findById(connection, command.tournamentId).map { tournament =>
          riichinexus.microservices.tournament.tables.tournaments.TournamentTable.save(connection, TournamentFunctions.whitelistPlayer(tournament, command.playerId))
        }
      }
    yield tournament

  private def ensurePlayerCanBeWhitelisted(player: Player, playerId: PlayerId): Unit =
    if player.status != PlayerStatus.Active then
      throw IllegalArgumentException(s"Player ${playerId.value} cannot be whitelisted")

  private final case class WhitelistPlayerCommand(
      tournamentId: TournamentId,
      playerId: PlayerId,
      actor: AccessPrincipal
  )

