package riichinexus.system.api.runtime

import riichinexus.system.postgres.JdbcConnectionFactory
import riichinexus.microservices.tournament.appeal.domain.AppealApplicationService
import riichinexus.microservices.tournament.domain.paifumanagement.functions.TournamentPaifuArchiveService
import riichinexus.microservices.tournament.domain.settlementmanagement.functions.TournamentSettlementCoordinator
import riichinexus.microservices.tournament.domain.tournamentmanagement.functions.TournamentStageCompletionCoordinator

final case class ApiExecutionContext(
    connectionFactory: JdbcConnectionFactory,
    tournamentPaifuArchiveService: TournamentPaifuArchiveService,
    tournamentSettlementCoordinator: TournamentSettlementCoordinator,
    tournamentStageCompletionCoordinator: TournamentStageCompletionCoordinator,
    tournamentAppealService: AppealApplicationService,
    storageLabel: String
)
