package riichinexus.api.runtime

import riichinexus.bootstrap.*
import riichinexus.microservices.auth.domain.AuthorizationPolicy
import riichinexus.infrastructure.postgres.JdbcConnectionFactory

final case class ApiExecutionContext(
    connectionFactory: JdbcConnectionFactory,
    authModule: AuthModuleContext,
    playerModule: PlayerModuleContext,
    clubModule: ClubModuleContext,
    opsAnalyticsModule: OpsAnalyticsModuleContext,
    tournamentModule: TournamentModuleContext,
    platformAdminModule: PlatformAdminModuleContext,
    tournamentAppealModule: TournamentAppealModuleContext,
    authorizationService: AuthorizationPolicy,
    storageLabel: String
)
