package riichinexus.microservices.tournament.api
import riichinexus.microservices.auth.objects.Permission
import riichinexus.microservices.auth.utils.{ResolveAccessPrincipal, ResolveGuestAccessPrincipal, ResolveRequestActor}
import riichinexus.microservices.auth.api.AuthCheckPermissionAPIMessage

import riichinexus.microservices.auth.domain.functions.{AccessPrincipalFunctions, AuthorizationPolicyFunctions, RoleGrantFunctions}

import riichinexus.microservices.tournament.objects.rulesmanagement.stageprogression.AdvancementRuleType
import riichinexus.microservices.tournament.objects.tournamentmanagement.TournamentFormat

import java.util.NoSuchElementException
import java.time.Instant

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
import riichinexus.microservices.tournament.domain.rulesmanagement.functions.knockout.KnockoutStageCoordinator
import riichinexus.microservices.tournament.domain.lineupmanagement.model.*
import riichinexus.microservices.tournament.domain.recordmanagement.model.*
import riichinexus.microservices.tournament.domain.settlementmanagement.model.*
import riichinexus.microservices.tournament.domain.tablemanagement.model.*
import riichinexus.microservices.tournament.domain.tournamentmanagement.model.*
import riichinexus.system.json.JsonCodecs.given
import riichinexus.microservices.tournament.domain.tablemanagement.model.Table
import riichinexus.microservices.tournament.objects.lineupmanagement.apiTypes.*
import riichinexus.microservices.tournament.objects.paifumanagement.apiTypes.*
import riichinexus.microservices.tournament.objects.recordmanagement.apiTypes.*
import riichinexus.microservices.tournament.objects.rulesmanagement.apiTypes.*
import riichinexus.microservices.tournament.objects.settlementmanagement.apiTypes.*
import riichinexus.microservices.tournament.objects.tablemanagement.apiTypes.*
import riichinexus.microservices.tournament.objects.tournamentmanagement.apiTypes.*
import riichinexus.microservices.tournament.objects.tournamentmanagement.apiTypes.AssignTournamentAdminRequest.given
import upickle.default.*

final case class TournamentStageAdvanceAPIMessage(tournamentId: String, stageId: String, operatorId: Option[String] = None) extends APIMessage[Vector[TournamentTableView]] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[Vector[TournamentTableView]] =
    for
      actor <- IO.blocking(operatorId.filter(_.nonEmpty).map(PlayerId(_)).map(ResolveAccessPrincipal(_).resolve(context.connection)).getOrElse(AccessPrincipalFunctions.system))
      at <- IO.realTimeInstant
      command = AdvanceKnockoutStageCommand(
        tournamentId = TournamentId(tournamentId),
        stageId = TournamentStageId(stageId),
        actor = actor,
        at = at
      )
      tables <- IO.blocking {
        {
          advanceStage(context.connection, command)
        }
      }
    yield tables.map(TournamentTableView.fromDomain)

  private def advanceStage(
      connection: java.sql.Connection,
      command: AdvanceKnockoutStageCommand
  ): Vector[Table] =
    val tournament = riichinexus.microservices.tournament.tables.tournaments.TournamentTable
      .findById(connection, command.tournamentId)
      .getOrElse(throw NoSuchElementException(s"Tournament ${command.tournamentId.value} was not found"))
    val stage = tournament.stages
      .find(_.id == command.stageId)
      .getOrElse(throw NoSuchElementException(s"Stage ${command.stageId.value} was not found"))
    AuthorizationPolicyFunctions.requirePermission(AuthorizationPolicyFunctions.strict, 
      command.actor,
      Permission.ManageTournamentStages,
      tournamentId = Some(command.tournamentId)
    )
    ensureKnockoutStage(stage, command.stageId)
    KnockoutStageCoordinator.materializeUnlockedTables(
      connection,
      command.tournamentId,
      command.stageId,
      command.at
    )

  private def ensureKnockoutStage(stage: TournamentStage, stageId: TournamentStageId): Unit =
    val isKnockoutStage =
      stage.format == TournamentFormat.Knockout ||
        stage.format == TournamentFormat.Finals ||
        stage.advancementRule.ruleType == AdvancementRuleType.KnockoutElimination
    if !isKnockoutStage then
      throw IllegalArgumentException(
        s"Stage ${stageId.value} is not configured as a knockout stage"
      )

  private final case class AdvanceKnockoutStageCommand(
      tournamentId: TournamentId,
      stageId: TournamentStageId,
      actor: AccessPrincipal,
      at: Instant
  )
