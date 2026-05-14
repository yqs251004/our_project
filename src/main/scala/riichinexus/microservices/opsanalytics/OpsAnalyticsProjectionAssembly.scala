package riichinexus.microservices.opsanalytics

import riichinexus.application.ports.DomainEventSubscriber
import riichinexus.bootstrap.ApplicationRepositoryContext
import riichinexus.domain.service.PairwiseEloRatingService
import riichinexus.microservices.dictionary.api.DictionaryBackedRatingConfigProvider
import riichinexus.microservices.opsanalytics.api.{
  AdvancedStatsPipelineService,
  AdvancedStatsProjectionSubscriber,
  ClubProjectionSubscriber,
  DashboardProjectionSubscriber,
  EventCascadeProjectionSubscriber,
  RatingProjectionSubscriber
}

object OpsAnalyticsProjectionAssembly:
  def subscribers(
      repositories: ApplicationRepositoryContext,
      advancedStatsService: AdvancedStatsPipelineService
  ): Vector[DomainEventSubscriber] =
    Vector[DomainEventSubscriber](
      RatingProjectionSubscriber(
        repositories.playerRepository,
        PairwiseEloRatingService(DictionaryBackedRatingConfigProvider(repositories.globalDictionaryRepository))
      ),
      ClubProjectionSubscriber(
        repositories.clubRepository,
        repositories.playerRepository,
        repositories.globalDictionaryRepository
      ),
      DashboardProjectionSubscriber(
        repositories.matchRecordRepository,
        repositories.paifuRepository,
        repositories.playerRepository,
        repositories.clubRepository,
        repositories.dashboardRepository
      ),
      AdvancedStatsProjectionSubscriber(advancedStatsService),
      EventCascadeProjectionSubscriber(
        repositories.playerRepository,
        repositories.clubRepository,
        repositories.dashboardRepository,
        repositories.advancedStatsBoardRepository,
        repositories.eventCascadeRecordRepository,
        advancedStatsService,
        repositories.globalDictionaryRepository
      )
    )
