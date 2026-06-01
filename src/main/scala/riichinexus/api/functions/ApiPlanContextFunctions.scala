package riichinexus.api.functions

import riichinexus.api.ApiPlanContext
import riichinexus.domain.service.AuthenticationFailure

object ApiPlanContextFunctions:

  def requireBearerToken(context: ApiPlanContext): String =
    context.bearerToken
      .map(_.trim)
      .filter(_.nonEmpty)
      .getOrElse(throw AuthenticationFailure("Bearer token is required", "missing_token"))
