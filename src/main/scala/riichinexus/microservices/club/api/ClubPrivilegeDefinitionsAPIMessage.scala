package riichinexus.microservices.club.api

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.domain.model.*
import riichinexus.microservices.club.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.club.objects.{ClubPrivilegeDefinition as ClubPrivilegeDefinitionResponse}
import upickle.default.*

final case class ClubPrivilegeDefinitionsAPIMessage() extends APIMessage[Vector[ClubPrivilegeDefinitionResponse]] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[Vector[ClubPrivilegeDefinitionResponse]] =
    IO.blocking(ClubPrivilegeRegistry.definitions.map(ClubPrivilegeDefinitionResponse.fromDomain))
