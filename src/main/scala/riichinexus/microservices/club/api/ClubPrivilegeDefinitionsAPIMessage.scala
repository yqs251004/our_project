package riichinexus.microservices.club.api

import cats.effect.IO
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.microservices.club.domain.rankprivilegemanagement.functions.ClubPrivilegeRegistry

import riichinexus.microservices.club.objects.rankprivilegemanagement.ClubPrivilegeDefinition
import upickle.default.ReadWriter

/** 获取俱乐部权限定义。 */
final case class ClubPrivilegeDefinitionsAPIMessage() extends APIMessage[Vector[ClubPrivilegeDefinition]] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[Vector[ClubPrivilegeDefinition]] =
    IO.pure(ClubPrivilegeRegistry.definitions)
