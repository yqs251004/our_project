package riichinexus.bootstrap

import riichinexus.application.service.*
import riichinexus.domain.service.*
import riichinexus.infrastructure.memory.*

final case class ApplicationContext(
    playerService: PlayerApplicationService,
    clubService: ClubApplicationService,
    tournamentService: TournamentApplicationService,
    tableService: TableLifecycleService,
    playerRepository: InMemoryPlayerRepository,
    clubRepository: InMemoryClubRepository,
    tournamentRepository: InMemoryTournamentRepository,
    tableRepository: InMemoryTableRepository,
    paifuRepository: InMemoryPaifuRepository,
    dashboardRepository: InMemoryDashboardRepository,
    eventBus: InMemoryDomainEventBus
)

object ApplicationContext:
  def inMemory(): ApplicationContext =
    val playerRepository = InMemoryPlayerRepository()
    val clubRepository = InMemoryClubRepository()
    val tournamentRepository = InMemoryTournamentRepository()
    val tableRepository = InMemoryTableRepository()
    val paifuRepository = InMemoryPaifuRepository()
    val dashboardRepository = InMemoryDashboardRepository()

    val eventBus = InMemoryDomainEventBus()
    eventBus.register(
      RatingProjectionSubscriber(playerRepository, PairwiseEloRatingService())
    )
    eventBus.register(ClubProjectionSubscriber(clubRepository, playerRepository))
    eventBus.register(
      DashboardProjectionSubscriber(
        paifuRepository,
        playerRepository,
        clubRepository,
        dashboardRepository
      )
    )

    ApplicationContext(
      playerService = PlayerApplicationService(playerRepository),
      clubService = ClubApplicationService(clubRepository, playerRepository),
      tournamentService = TournamentApplicationService(
        tournamentRepository,
        playerRepository,
        clubRepository,
        tableRepository,
        BalancedEloSeatingPolicy()
      ),
      tableService = TableLifecycleService(tableRepository, paifuRepository, eventBus),
      playerRepository = playerRepository,
      clubRepository = clubRepository,
      tournamentRepository = tournamentRepository,
      tableRepository = tableRepository,
      paifuRepository = paifuRepository,
      dashboardRepository = dashboardRepository,
      eventBus = eventBus
    )
