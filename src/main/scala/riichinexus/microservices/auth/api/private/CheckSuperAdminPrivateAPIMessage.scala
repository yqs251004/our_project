package riichinexus.microservices.auth.api.`private`

import cats.effect.IO
import riichinexus.microservices.auth.domain.functions.AccessPrincipalFunctions
import riichinexus.microservices.auth.domain.functions.AccessPrincipalPrivateViewFunctions
import riichinexus.microservices.auth.objects.`private`.AccessPrincipalPrivateView
import riichinexus.system.api.{APIMessage, ApiPlanContext}

/** 供后端服务检查访问主体是否为超级管理员。 */
final case class CheckSuperAdminPrivateAPIMessage(
    principal: AccessPrincipalPrivateView
) extends APIMessage[Boolean]:

  override def plan(context: ApiPlanContext): IO[Boolean] =
    IO.blocking(AccessPrincipalFunctions.isSuperAdmin(AccessPrincipalPrivateViewFunctions.toDomain(principal)))
