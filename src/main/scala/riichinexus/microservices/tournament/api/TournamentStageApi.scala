package riichinexus.microservices.tournament.api

import riichinexus.domain.model.*
import riichinexus.microservices.shared.api.requests.OperatorRequest
import riichinexus.microservices.tournament.api.requests.*
import riichinexus.microservices.tournament.api.responses.TournamentMutationView
import riichinexus.microservices.tournament.objects.StageTableQuery
import riichinexus.microservices.tournament.tables.TournamentTables

object TournamentStageApi:

  def addStage(
      service: TournamentApplicationService,
      principalOf: PlayerId => AccessPrincipal,
      tournamentId: TournamentId,
      request: CreateTournamentStageRequest
  ): Option[Tournament] =
    service.addStage(
      tournamentId = tournamentId,
      stage = request.toStage,
      actor = request.operator.map(principalOf).getOrElse(AccessPrincipal.system)
    )

  def configureStageRules(
      service: TournamentApplicationService,
      principalOf: PlayerId => AccessPrincipal,
      tournamentId: TournamentId,
      stageId: TournamentStageId,
      request: ConfigureStageRulesRequest
  ): Option[Tournament] =
    service.configureStageRules(
      tournamentId = tournamentId,
      stageId = stageId,
      advancementRule = request.advancementRule,
      swissRule = request.swissRule,
      knockoutRule = request.knockoutRule,
      schedulingPoolSize = request.schedulingPoolSize,
      actor = principalOf(request.operator)
    )

  def submitLineup(
      service: TournamentApplicationService,
      views: TournamentViewAssembler,
      principalOf: PlayerId => AccessPrincipal,
      tournamentId: TournamentId,
      stageId: TournamentStageId,
      request: SubmitStageLineupRequest
  ): Option[TournamentMutationView] =
    service.submitLineup(
      tournamentId = tournamentId,
      stageId = stageId,
      submission = request.toSubmission,
      actor = principalOf(request.operator)
    )
    views.buildTournamentMutationView(
      tournamentId = tournamentId,
      scheduledTables = Vector.empty
    )

  def scheduleStageTables(
      service: TournamentApplicationService,
      views: TournamentViewAssembler,
      principalOf: PlayerId => AccessPrincipal,
      tournamentId: TournamentId,
      stageId: TournamentStageId,
      request: Option[OperatorRequest]
  ): Option[TournamentMutationView] =
    val scheduledTables = service.scheduleStageTables(
      tournamentId,
      stageId,
      request.flatMap(_.operator).map(principalOf).getOrElse(AccessPrincipal.system)
    )
    views.buildTournamentMutationView(tournamentId, scheduledTables)

  def stageStandings(
      stageQueries: TournamentStageQueryService,
      tournamentId: TournamentId,
      stageId: TournamentStageId
  ): StageRankingSnapshot =
    stageQueries.stageStandings(tournamentId, stageId)

  def stageTables(
      tables: TournamentTables,
      tournamentId: TournamentId,
      stageId: TournamentStageId,
      query: StageTableQuery
  ): Vector[Table] =
    tables.listStageTables(tournamentId, stageId, query)

  def stageAdvancementPreview(
      stageQueries: TournamentStageQueryService,
      tournamentId: TournamentId,
      stageId: TournamentStageId
  ) =
    stageQueries.stageAdvancementPreview(tournamentId, stageId)

  def stageKnockoutBracket(
      stageQueries: TournamentStageQueryService,
      tournamentId: TournamentId,
      stageId: TournamentStageId
  ) =
    stageQueries.stageKnockoutBracket(tournamentId, stageId)

  def advanceKnockoutStage(
      service: TournamentApplicationService,
      principalOf: PlayerId => AccessPrincipal,
      tournamentId: TournamentId,
      stageId: TournamentStageId,
      request: AdvanceKnockoutStageRequest
  ) =
    service.advanceKnockoutStage(
      tournamentId = tournamentId,
      stageId = stageId,
      actor = principalOf(request.operator)
    )

  def completeStage(
      service: TournamentApplicationService,
      principalOf: PlayerId => AccessPrincipal,
      tournamentId: TournamentId,
      stageId: TournamentStageId,
      request: CompleteStageRequest
  ): Option[StageAdvancementSnapshot] =
    service.completeStage(
      tournamentId = tournamentId,
      stageId = stageId,
      actor = principalOf(request.operator)
    )
