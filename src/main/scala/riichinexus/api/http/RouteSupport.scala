package riichinexus.api.http

import riichinexus.api.runtime.ApiPlanSupport

final case class RouteSupport(
    routeContext: RouteContext,
    apiPlanSupport: ApiPlanSupport
)
