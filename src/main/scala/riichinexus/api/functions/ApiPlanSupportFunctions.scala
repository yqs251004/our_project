package riichinexus.api.functions

import riichinexus.api.runtime.{ApiExecutionContext, ApiPlanSupport}

object ApiPlanSupportFunctions:

  def fromExecutionContext(executionContext: ApiExecutionContext): ApiPlanSupport =
    ApiPlanSupport(
      executionContext = executionContext,
      authModule = executionContext.authModule,
      playerModule = executionContext.playerModule,
      clubModule = executionContext.clubModule,
      opsAnalyticsModule = executionContext.opsAnalyticsModule,
      tournamentModule = executionContext.tournamentModule,
      platformAdminModule = executionContext.platformAdminModule,
      tournamentAppealModule = executionContext.tournamentAppealModule,
      authorizationService = executionContext.authorizationService
    )
