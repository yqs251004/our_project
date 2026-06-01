package riichinexus.api.http.functions

import riichinexus.api.http.RouteContext

object RouteContextFunctions:

  def storageLabel(routeContext: RouteContext): String =
    routeContext.executionContext.storageLabel
