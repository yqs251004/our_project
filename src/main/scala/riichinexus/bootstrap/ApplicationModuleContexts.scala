package riichinexus.bootstrap

import riichinexus.microservices.auth.api.{AuthApplicationService, GuestSessionApplicationService}
import riichinexus.microservices.auth.tables.AuthTables
import riichinexus.microservices.club.api.{ClubApplicationService, ClubViewAssembler}
import riichinexus.microservices.club.tables.ClubTables
import riichinexus.microservices.dictionary.api.DictionaryGovernanceService
import riichinexus.microservices.dictionary.tables.DictionaryTables
import riichinexus.microservices.opsanalytics.api.{
  AdvancedStatsPipelineService,
  DemoScenarioService,
  DomainEventOperationsService,
  PerformanceDiagnosticsService
}
import riichinexus.microservices.opsanalytics.tables.OpsAnalyticsTables
import riichinexus.microservices.platformadmin.api.SuperAdminService
import riichinexus.microservices.platformadmin.tables.PlatformAdminTables
import riichinexus.microservices.player.api.PlayerApplicationService
import riichinexus.microservices.player.tables.PlayerTables
import riichinexus.microservices.publicquery.api.PublicQueryService
import riichinexus.microservices.publicquery.tables.PublicQueryTables
import riichinexus.microservices.tournament.api.{
  TableLifecycleService,
  TournamentApplicationService,
  TournamentStageQueryService,
  TournamentViewAssembler
}
import riichinexus.microservices.tournament.appeal.api.AppealApplicationService
import riichinexus.microservices.tournament.appeal.tables.TournamentAppealTables
import riichinexus.microservices.tournament.tables.TournamentTables

final case class AuthModuleContext(
    tables: AuthTables,
    authService: AuthApplicationService,
    guestSessionService: GuestSessionApplicationService
)

final case class PlayerModuleContext(
    tables: PlayerTables,
    service: PlayerApplicationService
)

final case class ClubModuleContext(
    tables: ClubTables,
    service: ClubApplicationService,
    views: ClubViewAssembler,
    tournamentService: TournamentApplicationService,
    tournamentViews: TournamentViewAssembler
)

final case class DictionaryModuleContext(
    tables: DictionaryTables,
    governance: DictionaryGovernanceService
)

final case class PublicQueryModuleContext(
    tables: PublicQueryTables,
    service: PublicQueryService,
    clubViews: ClubViewAssembler,
    tournamentViews: TournamentViewAssembler
)

final case class TournamentModuleContext(
    tables: TournamentTables,
    service: TournamentApplicationService,
    stageQueries: TournamentStageQueryService,
    views: TournamentViewAssembler,
    tableService: TableLifecycleService
)

final case class OpsAnalyticsModuleContext(
    tables: OpsAnalyticsTables,
    advancedStatsService: AdvancedStatsPipelineService,
    demoScenarioService: DemoScenarioService,
    domainEventService: DomainEventOperationsService,
    performanceDiagnosticsService: PerformanceDiagnosticsService
)

final case class PlatformAdminModuleContext(
    tables: PlatformAdminTables,
    service: SuperAdminService
)

final case class TournamentAppealModuleContext(
    tables: TournamentAppealTables,
    service: AppealApplicationService
)
