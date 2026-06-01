package riichinexus.api.runtime

import riichinexus.bootstrap.*
import riichinexus.microservices.auth.domain.AuthorizationPolicy

final case class ApiPlanSupport(
    executionContext: ApiExecutionContext,
    authModule: AuthModuleContext,
    playerModule: PlayerModuleContext,
    clubModule: ClubModuleContext,
    opsAnalyticsModule: OpsAnalyticsModuleContext,
    tournamentModule: TournamentModuleContext,
    platformAdminModule: PlatformAdminModuleContext,
    tournamentAppealModule: TournamentAppealModuleContext,
    authorizationService: AuthorizationPolicy
)
