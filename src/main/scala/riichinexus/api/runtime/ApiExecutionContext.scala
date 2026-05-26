package riichinexus.api.runtime

import riichinexus.bootstrap.*
import riichinexus.domain.service.AuthorizationService
import riichinexus.infrastructure.postgres.JdbcConnectionFactory

final case class ApiExecutionContext(
    connectionFactory: JdbcConnectionFactory,
    authModule: AuthModuleContext,
    playerModule: PlayerModuleContext,
    clubModule: ClubModuleContext,
    dictionaryModule: DictionaryModuleContext,
    publicQueryModule: PublicQueryModuleContext,
    opsAnalyticsModule: OpsAnalyticsModuleContext,
    tournamentModule: TournamentModuleContext,
    platformAdminModule: PlatformAdminModuleContext,
    tournamentAppealModule: TournamentAppealModuleContext,
    authorizationService: AuthorizationService,
    storageLabel: String
)
