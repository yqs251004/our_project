package riichinexus.microservices.auth.api.`private`

import cats.effect.IO
import riichinexus.microservices.auth.domain.functions.AccessPrincipalFunctions
import riichinexus.microservices.auth.domain.functions.AccessPrincipalPrivateViewFunctions
import riichinexus.microservices.auth.objects.`private`.AccessPrincipalPrivateView
import riichinexus.system.api.{APIMessage, ApiPlanContext}

import upickle.default.ReadWriter

/** 供后端服务解析系统访问主体。 */
final case class ResolveSystemAccessPrincipalPrivateAPIMessage()
    extends APIMessage[AccessPrincipalPrivateView] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[AccessPrincipalPrivateView] =
    IO.blocking(AccessPrincipalPrivateViewFunctions.toPrivateView(AccessPrincipalFunctions.system))
