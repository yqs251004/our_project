package riichinexus.microservices.club.api.rankprivilege
import cats.effect.IO
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.microservices.club.domain.rankprivilege.functions.ClubPrivilegeRegistry

import riichinexus.microservices.club.objects.rankprivilege.ClubPrivilegeDefinition
/** 获取俱乐部权限定义。 */
final case class ClubPrivilegeDefinitionsAPIMessage() extends APIMessage[Vector[ClubPrivilegeDefinition]]:

  override def plan(context: ApiPlanContext): IO[Vector[ClubPrivilegeDefinition]] =
    IO.pure(ClubPrivilegeRegistry.definitions)
