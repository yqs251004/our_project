package riichinexus.api.http

import riichinexus.bootstrap.*
import riichinexus.domain.service.AuthorizationService

final case class RouteContext(
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
    storageLabel: String,
    corsAllowOrigin: String
)
