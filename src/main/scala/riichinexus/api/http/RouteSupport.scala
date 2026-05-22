package riichinexus.api.http

import riichinexus.api.runtime.ApiPlanSupport

final class RouteSupport(
    val routeContext: RouteContext
) extends HttpResponseSupport
    with HttpRequestSupport
    with HttpPaginationSupport
    with HttpOpenApiSupport:
  val apiPlanSupport: ApiPlanSupport = ApiPlanSupport(routeContext.executionContext)
  val storageLabel: String = routeContext.storageLabel

object RouteSupport:

  def apply(routeContext: RouteContext): RouteSupport =
    new RouteSupport(routeContext)
