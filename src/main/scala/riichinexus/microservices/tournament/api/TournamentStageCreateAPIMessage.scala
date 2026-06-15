package riichinexus.microservices.tournament.api
import riichinexus.microservices.auth.objects.Permission
import riichinexus.microservices.auth.utils.{ResolveAccessPrincipal, ResolveGuestAccessPrincipal, ResolveRequestActor}
import riichinexus.microservices.auth.api.AuthCheckPermissionAPIMessage

import riichinexus.microservices.auth.domain.functions.{AccessPrincipalFunctions, AuthorizationPolicyFunctions, RoleGrantFunctions}

import riichinexus.microservices.tournament.objects.rulesmanagement.stageprogression.{AdvancementRule, AdvancementRuleType}
import riichinexus.microservices.tournament.objects.rulesmanagement.knockout.KnockoutRuleConfig
import riichinexus.microservices.tournament.objects.rulesmanagement.swiss.SwissRuleConfig
import riichinexus.microservices.tournament.objects.tournamentmanagement.TournamentStatus

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
import riichinexus.microservices.tournament.domain.rulesmanagement.functions.stageprogression.AdvancementRuleFunctions
import riichinexus.microservices.tournament.domain.tournamentmanagement.functions.TournamentFunctions
import riichinexus.microservices.tournament.domain.lineupmanagement.model.*
import riichinexus.microservices.tournament.domain.recordmanagement.model.*
import riichinexus.microservices.tournament.domain.settlementmanagement.model.*
import riichinexus.microservices.tournament.domain.tablemanagement.model.*
import riichinexus.microservices.tournament.domain.tournamentmanagement.model.*
import riichinexus.system.json.JsonCodecs.given
import riichinexus.microservices.tournament.domain.tournamentmanagement.functions.TournamentRuntimeDefaults
import riichinexus.microservices.tournament.mahjongcore.objects.gamestate.MahjongRuleset
import riichinexus.microservices.tournament.objects.lineupmanagement.apiTypes.*
import riichinexus.microservices.tournament.objects.paifumanagement.apiTypes.*
import riichinexus.microservices.tournament.objects.recordmanagement.apiTypes.*
import riichinexus.microservices.tournament.objects.rulesmanagement.apiTypes.*
import riichinexus.microservices.tournament.objects.settlementmanagement.apiTypes.*
import riichinexus.microservices.tournament.objects.tablemanagement.apiTypes.*
import riichinexus.microservices.tournament.objects.tournamentmanagement.apiTypes.*
import riichinexus.microservices.tournament.objects.tournamentmanagement.apiTypes.AssignTournamentAdminRequest.given
import upickle.default.*

final case class TournamentStageCreateAPIMessage(tournamentId: String, request: CreateTournamentStageRequest) extends APIMessage[TournamentSummaryView] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[TournamentSummaryView] =
    for
      actor <- request.operatorId.map(PlayerId(_)).map(ResolveAccessPrincipal(_).plan(context)).getOrElse(IO.pure(AccessPrincipalFunctions.system))
      command = CreateStageCommand(
        tournamentId = TournamentId(tournamentId),
        actor = actor,
        stage = tournamentStage(request)
      )
      tournament <- IO.blocking {
        {
          createStage(context.connection, command)
        }.getOrElse(throw NoSuchElementException("Resource not found"))
      }
    yield TournamentSummaryView.fromDomain(tournament)

  private def createStage(
      connection: java.sql.Connection,
      command: CreateStageCommand
  ): Option[Tournament] =
    riichinexus.microservices.tournament.tables.tournaments.TournamentTable.findById(connection, command.tournamentId).map { tournament =>
      ensureStageCanBeAdded(tournament, command)
      riichinexus.microservices.tournament.tables.tournaments.TournamentTable.save(connection, TournamentFunctions.addStage(tournament, TournamentRuntimeDefaults.normalizeStage(command.stage)))
    }

  private def ensureStageCanBeAdded(
      tournament: Tournament,
      command: CreateStageCommand
  ): Unit =
    if tournament.status == TournamentStatus.Completed || tournament.status == TournamentStatus.Archived then
      throw IllegalArgumentException(
        s"Cannot add stages to tournament ${command.tournamentId.value} in status ${tournament.status}"
      )
    AuthorizationPolicyFunctions.requirePermission(AuthorizationPolicyFunctions.strict, 
      command.actor,
      Permission.ManageTournamentStages,
      tournamentId = Some(command.tournamentId)
    )

  private final case class CreateStageCommand(
      tournamentId: TournamentId,
      actor: AccessPrincipal,
      stage: TournamentStage
  )

  private def tournamentStage(request: CreateTournamentStageRequest): TournamentStage =
    TournamentStage(
      id = request.id.map(TournamentStageId(_)).getOrElse(TournamentIdGenerator.stageId()),
      name = request.name,
      format = request.format,
      order = request.order,
      roundCount = request.roundCount,
      advancementRule = request.advancementRuleType
        .map(rule =>
          AdvancementRule(
            ruleType = AdvancementRuleType.valueOf(rule),
            cutSize = request.cutSize,
            thresholdScore = request.thresholdScore,
            targetTableCount = request.targetTableCount,
            templateKey = request.ruleTemplateKey,
            note = request.note
          )
        )
        .getOrElse(
          AdvancementRuleFunctions.defaultFor(request.format).copy(
            templateKey = request.ruleTemplateKey,
            note = request.note.orElse(AdvancementRuleFunctions.defaultFor(request.format).note)
          )
      ),
      swissRule = swissRule(request),
      knockoutRule = knockoutRule(request),
      mahjongRuleset = request.mahjongRuleset.getOrElse(MahjongRuleset()),
      schedulingPoolSize = request.schedulingPoolSize.getOrElse(4)
    )

  private def swissRule(request: CreateTournamentStageRequest): Option[SwissRuleConfig] =
    if request.pairingMethod.isDefined || request.carryOverPoints.isDefined || request.maxRounds.isDefined then
      Some(
        SwissRuleConfig(
          pairingMethod = request.pairingMethod.map(_.trim.toLowerCase).getOrElse("balanced-elo"),
          carryOverPoints = request.carryOverPoints.getOrElse(true),
          maxRounds = request.maxRounds
        )
      )
    else None

  private def knockoutRule(request: CreateTournamentStageRequest): Option[KnockoutRuleConfig] =
    if request.bracketSize.isDefined || request.thirdPlaceMatch.isDefined || request.seedingPolicy.isDefined || request.repechageEnabled.isDefined then
      Some(
        KnockoutRuleConfig(
          bracketSize = request.bracketSize,
          thirdPlaceMatch = request.thirdPlaceMatch.getOrElse(false),
          seedingPolicy = request.seedingPolicy.map(_.trim.toLowerCase).getOrElse("rating"),
          repechageEnabled = request.repechageEnabled.getOrElse(false)
        )
      )
    else None
