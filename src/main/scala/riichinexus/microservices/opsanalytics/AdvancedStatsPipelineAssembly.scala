package riichinexus.microservices.opsanalytics

import riichinexus.application.ports.TransactionManager
import riichinexus.bootstrap.ApplicationRepositoryContext
import riichinexus.microservices.opsanalytics.api.AdvancedStatsPipelineService

object AdvancedStatsPipelineAssembly:
  def build(
      repositories: ApplicationRepositoryContext,
      transactionManager: TransactionManager
  ): AdvancedStatsPipelineService =
    AdvancedStatsPipelineService(
      repositories.paifuRepository,
      repositories.matchRecordRepository,
      repositories.playerRepository,
      repositories.clubRepository,
      repositories.advancedStatsBoardRepository,
      repositories.advancedStatsRecomputeTaskRepository,
      transactionManager
    )
