package riichinexus.microservices.club.api

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.microservices.club.domain.model.ClubPrivilegeRegistry
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.club.objects.ClubPrivilegeDefinition
import upickle.default.*

final case class ClubPrivilegeDefinitionsAPIMessage() extends APIMessage[Vector[ClubPrivilegeDefinition]] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[Vector[ClubPrivilegeDefinition]] =
    IO.blocking(ClubPrivilegeRegistry.definitions)
