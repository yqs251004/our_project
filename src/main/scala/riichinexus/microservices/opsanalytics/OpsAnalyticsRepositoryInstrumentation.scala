package riichinexus.microservices.opsanalytics

import riichinexus.bootstrap.ApplicationRepositoryContext
import riichinexus.microservices.opsanalytics.api.{
  InstrumentedClubRepository,
  InstrumentedGlobalDictionaryRepository,
  InstrumentedMatchRecordRepository,
  InstrumentedPlayerRepository,
  InstrumentedTableRepository,
  InstrumentedTournamentRepository,
  PerformanceDiagnosticsService
}

object OpsAnalyticsRepositoryInstrumentation:
  def instrument(
      repositories: ApplicationRepositoryContext,
      diagnostics: PerformanceDiagnosticsService
  ): ApplicationRepositoryContext =
    repositories.copy(
      playerRepository =
        new InstrumentedPlayerRepository(repositories.playerRepository, diagnostics),
      clubRepository =
        new InstrumentedClubRepository(repositories.clubRepository, diagnostics),
      globalDictionaryRepository =
        new InstrumentedGlobalDictionaryRepository(
          repositories.globalDictionaryRepository,
          diagnostics
        ),
      tournamentRepository =
        new InstrumentedTournamentRepository(repositories.tournamentRepository, diagnostics),
      tableRepository =
        new InstrumentedTableRepository(repositories.tableRepository, diagnostics),
      matchRecordRepository =
        new InstrumentedMatchRecordRepository(repositories.matchRecordRepository, diagnostics)
    )
