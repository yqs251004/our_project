package riichinexus.microservices.tournament.api
import riichinexus.microservices.auth.objects.Permission
import riichinexus.microservices.auth.utils.{ResolveAccessPrincipal, ResolveGuestAccessPrincipal, ResolveRequestActor}
import riichinexus.microservices.auth.api.AuthCheckPermissionAPIMessage

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
import riichinexus.microservices.tournament.domain.tournamentmanagement.functions.{TournamentFunctions, TournamentStageFunctions}
import riichinexus.microservices.tournament.domain.lineupmanagement.model.*
import riichinexus.microservices.tournament.domain.recordmanagement.model.*
import riichinexus.microservices.tournament.domain.settlementmanagement.model.*
import riichinexus.microservices.tournament.domain.tablemanagement.model.*
import riichinexus.microservices.tournament.domain.tournamentmanagement.model.*
import riichinexus.system.json.JsonCodecs.given
import riichinexus.microservices.tournament.domain.tournamentmanagement.functions.TournamentRuntimeDefaults
import riichinexus.microservices.tournament.mahjongcore.objects.gamestate.MahjongRuleset
import riichinexus.microservices.tournament.objects.rulesmanagement.stageprogression.{AdvancementRule, AdvancementRuleType}
import riichinexus.microservices.tournament.objects.rulesmanagement.knockout.KnockoutRuleConfig
import riichinexus.microservices.tournament.objects.rulesmanagement.swiss.SwissRuleConfig
import riichinexus.microservices.tournament.objects.lineupmanagement.apiTypes.*
import riichinexus.microservices.tournament.objects.paifumanagement.apiTypes.*
import riichinexus.microservices.tournament.objects.recordmanagement.apiTypes.*
import riichinexus.microservices.tournament.objects.rulesmanagement.apiTypes.*
import riichinexus.microservices.tournament.objects.settlementmanagement.apiTypes.*
import riichinexus.microservices.tournament.objects.tablemanagement.apiTypes.*
import riichinexus.microservices.tournament.objects.tournamentmanagement.apiTypes.*
import riichinexus.microservices.tournament.objects.tournamentmanagement.apiTypes.AssignTournamentAdminRequest.given
import upickle.default.*

final case class TournamentStageConfigureRulesAPIMessage(tournamentId: String, stageId: String, request: ConfigureStageRulesRequest) extends APIMessage[TournamentSummaryView] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[TournamentSummaryView] =
    for
      actor <- ResolveAccessPrincipal(PlayerId(request.operatorId)).plan(context)
      command = ConfigureStageRulesCommand(
        tournamentId = TournamentId(tournamentId),
        stageId = TournamentStageId(stageId),
        actor = actor,
        request = request
      )
      tournament <- IO.blocking {
        {
          configureStageRules(context.connection, command)
        }.getOrElse(throw NoSuchElementException("Resource not found"))
      }
    yield TournamentSummaryView.fromDomain(tournament)

  private def configureStageRules(
      connection: java.sql.Connection,
      command: ConfigureStageRulesCommand
  ): Option[Tournament] =
    riichinexus.microservices.tournament.tables.tournaments.TournamentTable.findById(connection, command.tournamentId).map { tournament =>
      val currentStage = requireStage(tournament, command.stageId)
      AuthorizationPolicyFunctions.requirePermission(AuthorizationPolicyFunctions.strict, 
        command.actor,
        Permission.ConfigureTournamentRules,
        tournamentId = Some(command.tournamentId)
      )
      val configuredStage = buildConfiguredStage(currentStage, command.request)
      riichinexus.microservices.tournament.tables.tournaments.TournamentTable.save(connection, 
        TournamentFunctions.updateStage(tournament, command.stageId, _ => configuredStage)
      )
    }

  private def requireStage(tournament: Tournament, stageId: TournamentStageId): TournamentStage =
    tournament.stages
      .find(_.id == stageId)
      .getOrElse(throw NoSuchElementException(s"Stage ${stageId.value} was not found"))

  private def buildConfiguredStage(
      currentStage: TournamentStage,
      request: ConfigureStageRulesRequest
  ): TournamentStage =
    validateRequest(request)
    val baseStage = currentStage.copy(
      format = request.format.getOrElse(currentStage.format),
      roundCount = math.max(request.roundCount.getOrElse(currentStage.roundCount), currentStage.currentRound)
    )
    TournamentRuntimeDefaults.normalizeStage(
      TournamentStageFunctions.withRules(
        baseStage,
        advancementRule(request),
        swissRule(request),
        knockoutRule(request),
        request.schedulingPoolSize.getOrElse(baseStage.schedulingPoolSize),
        request.mahjongRuleset.getOrElse(baseStage.mahjongRuleset)
      )
    )

  private def advancementRule(request: ConfigureStageRulesRequest): AdvancementRule =
    AdvancementRule(
      ruleType = request.advancementRuleType.map(AdvancementRuleType.valueOf).getOrElse(AdvancementRuleType.Custom),
      cutSize = request.cutSize,
      thresholdScore = request.thresholdScore,
      targetTableCount = request.targetTableCount,
      templateKey = request.ruleTemplateKey,
      note = request.note
    )

  private def swissRule(request: ConfigureStageRulesRequest): Option[SwissRuleConfig] =
    if request.pairingMethod.isDefined || request.carryOverPoints.isDefined || request.maxRounds.isDefined then
      Some(
        SwissRuleConfig(
          pairingMethod = request.pairingMethod.map(_.trim.toLowerCase).getOrElse("balanced-elo"),
          carryOverPoints = request.carryOverPoints.getOrElse(true),
          maxRounds = request.maxRounds
        )
      )
    else None

  private def knockoutRule(request: ConfigureStageRulesRequest): Option[KnockoutRuleConfig] =
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

  private def validateRequest(request: ConfigureStageRulesRequest): Unit =
    require(
      request.advancementRuleType.forall(_.trim.nonEmpty),
      "advancementRuleType must not be blank"
    )
    require(
      request.ruleTemplateKey.forall(_.trim.nonEmpty),
      "ruleTemplateKey must not be blank"
    )
    request.mahjongRuleset.foreach(validateMahjongRuleset)

  private def validateMahjongRuleset(ruleset: MahjongRuleset): Unit =
    require(ruleset.initialPoints > 0, "initialPoints must be positive")
    require(ruleset.targetPoints > 0, "targetPoints must be positive")
    require(ruleset.akaDoraCount >= 0 && ruleset.akaDoraCount <= 3, "akaDoraCount must be between 0 and 3")
    require(ruleset.minHan >= 1, "minHan must be positive")

  private final case class ConfigureStageRulesCommand(
      tournamentId: TournamentId,
      stageId: TournamentStageId,
      actor: AccessPrincipal,
      request: ConfigureStageRulesRequest
  )
