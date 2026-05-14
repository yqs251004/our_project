package riichinexus.microservices.opsanalytics.api

import java.time.{Duration, Instant}
import java.util.NoSuchElementException

import riichinexus.application.ports.*
import riichinexus.domain.event.*
import riichinexus.domain.model.*
import riichinexus.domain.service.*
import riichinexus.microservices.auth.api.GuestSessionApplicationService
import riichinexus.microservices.club.api.ClubApplicationService
import riichinexus.microservices.dictionary.api.RuntimeDictionarySupport
import riichinexus.microservices.opsanalytics.api.responses.*
import riichinexus.microservices.player.api.PlayerApplicationService
import riichinexus.microservices.publicquery.api.PublicQueryService
import riichinexus.microservices.tournament.api.{TableLifecycleService, TournamentApplicationService}
import riichinexus.microservices.tournament.appeal.api.AppealApplicationService

final class DemoScenarioService(
    protected val playerService: PlayerApplicationService,
    protected val guestSessionService: GuestSessionApplicationService,
    protected val publicQueryService: PublicQueryService,
    protected val clubService: ClubApplicationService,
    protected val tournamentService: TournamentApplicationService,
    protected val tableService: TableLifecycleService,
    protected val appealService: AppealApplicationService,
    protected val dashboardRepository: DashboardRepository,
    protected val advancedStatsBoardRepository: AdvancedStatsBoardRepository,
    protected val advancedStatsRecomputeTaskRepository: AdvancedStatsRecomputeTaskRepository,
    protected val advancedStatsPipelineService: AdvancedStatsPipelineService,
    protected val domainEventOutboxRepository: DomainEventOutboxRepository,
    protected val appealTicketRepository: AppealTicketRepository,
    protected val eventBus: DomainEventBus,
    protected val playerRepository: PlayerRepository,
    protected val guestSessionRepository: GuestSessionRepository,
    protected val clubRepository: ClubRepository,
    protected val tournamentRepository: TournamentRepository,
    protected val tableRepository: TableRepository,
    protected val matchRecordRepository: MatchRecordRepository
) extends DemoScenarioBootstrapSupport
    with DemoScenarioSnapshotSupport
    with DemoScenarioGuideSupport
    with DemoScenarioActionSupport:
  def currentScenario(
      variant: DemoScenarioVariant = DemoScenarioVariant.Basic,
      refreshDerived: Boolean = true
  ): Option[DemoScenarioSnapshot] =
    findScenario(variant).map { case (config, recommendedOperatorId, tournament, stage) =>
      if refreshDerived then flushDerivedViews()
      val refreshedTournament = tournamentRepository.findById(tournament.id).getOrElse(tournament)
      val refreshedStage = refreshedTournament.stages.find(_.id == config.stageId).getOrElse(stage)
      buildScenarioSnapshot(config, recommendedOperatorId, refreshedTournament, refreshedStage)
    }

  def refreshScenario(
      variant: DemoScenarioVariant = DemoScenarioVariant.Basic,
      bootstrapIfMissing: Boolean = true
  ): Option[DemoScenarioSnapshot] =
    currentScenario(variant = variant, refreshDerived = true)
      .orElse {
        if bootstrapIfMissing then Some(bootstrapScenario(variant = variant, refreshDerived = true))
        else None
      }

  def currentReadiness(
      variant: DemoScenarioVariant = DemoScenarioVariant.Basic,
      bootstrapIfMissing: Boolean = false,
      refreshDerived: Boolean = true
  ): Option[DemoScenarioReadiness] =
    val snapshot =
      if refreshDerived then refreshScenario(variant = variant, bootstrapIfMissing = bootstrapIfMissing)
      else
        currentScenario(variant = variant, refreshDerived = false)
          .orElse {
            if bootstrapIfMissing then Some(bootstrapScenario(variant = variant, refreshDerived = false))
            else None
          }
    snapshot.map(_.readiness)

  def guide(
      variant: DemoScenarioVariant = DemoScenarioVariant.Basic,
      bootstrapIfMissing: Boolean = true,
      refreshDerived: Boolean = true
  ): Option[DemoScenarioGuide] =
    val snapshot =
      if refreshDerived then refreshScenario(variant = variant, bootstrapIfMissing = bootstrapIfMissing)
      else
        currentScenario(variant = variant, refreshDerived = false)
          .orElse {
            if bootstrapIfMissing then Some(bootstrapScenario(variant = variant, refreshDerived = false))
            else None
          }

    snapshot.map(buildGuide)

  def widgets(
      variant: DemoScenarioVariant = DemoScenarioVariant.Basic,
      bootstrapIfMissing: Boolean = true,
      refreshDerived: Boolean = true
  ): Option[DemoScenarioWidgets] =
    val snapshot =
      if refreshDerived then refreshScenario(variant = variant, bootstrapIfMissing = bootstrapIfMissing)
      else
        currentScenario(variant = variant, refreshDerived = false)
          .orElse {
            if bootstrapIfMissing then Some(bootstrapScenario(variant = variant, refreshDerived = false))
            else None
          }

    snapshot.map(buildWidgets)

  def actionCatalog(
      variant: DemoScenarioVariant = DemoScenarioVariant.Basic,
      bootstrapIfMissing: Boolean = true,
      refreshDerived: Boolean = true
  ): Option[Vector[DemoScenarioActionSpec]] =
    val snapshot =
      if refreshDerived then refreshScenario(variant = variant, bootstrapIfMissing = bootstrapIfMissing)
      else
        currentScenario(variant = variant, refreshDerived = false)
          .orElse {
            if bootstrapIfMissing then Some(bootstrapScenario(variant = variant, refreshDerived = false))
            else None
          }

    snapshot.map(buildActionCatalog)

  def executeAction(
      variant: DemoScenarioVariant = DemoScenarioVariant.Basic,
      action: DemoScenarioActionCode,
      bootstrapIfMissing: Boolean = true
  ): Option[DemoScenarioActionResult] =
    val scenario =
      findScenario(variant).orElse {
        if bootstrapIfMissing then
          bootstrapScenario(variant = variant, refreshDerived = true)
          findScenario(variant)
        else None
      }

    scenario.map { case (config, recommendedOperatorId, tournament, stage) =>
      val actor = principalFor(recommendedOperatorId)
      val message =
        action match
          case DemoScenarioActionCode.RefreshScenario =>
            flushDerivedViews()
            s"Refreshed ${config.variant} demo scenario."
          case DemoScenarioActionCode.ResetScenario =>
            bootstrapScenario(variant = variant, refreshDerived = true)
            s"Reset ${config.variant} demo scenario to seeded baseline."
          case DemoScenarioActionCode.ArchiveNextTable =>
            archiveNextRunnableTable(config, tournament.id, stage.id, actor)
          case DemoScenarioActionCode.FileOpenAppeal =>
            createDemoAppeal(config, tournament.id, stage.id)
          case DemoScenarioActionCode.ResolveOldestAppeal =>
            resolveOldestDemoAppeal(config, tournament.id, stage.id, actor)

      val refreshedSnapshot = refreshScenario(variant = variant, bootstrapIfMissing = true)
        .getOrElse(throw NoSuchElementException(s"Demo scenario ${config.variant} was not found after action"))
      DemoScenarioActionResult(
        variant = config.variant,
        action = action,
        message = message,
        snapshot = refreshedSnapshot,
        widgets = buildWidgets(refreshedSnapshot)
      )
    }
