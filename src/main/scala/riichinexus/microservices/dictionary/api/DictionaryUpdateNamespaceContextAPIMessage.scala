package riichinexus.microservices.dictionary.api

import java.time.Instant

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.dictionary.domain.DictionaryNamespaceManagementOperations
import riichinexus.microservices.dictionary.objects.apiTypes.{DictionaryNamespaceRegistration as DictionaryNamespaceRegistrationResponse, *}
import upickle.default.*

final case class DictionaryUpdateNamespaceContextAPIMessage(
    operatorId: String,
    namespacePrefix: String,
    contextClubId: Option[String] = None,
    note: Option[String] = None
) extends APIMessage[DictionaryNamespaceRegistrationResponse] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[DictionaryNamespaceRegistrationResponse] =
    for
      request <- IO(UpdateDictionaryNamespaceContextRequest(operatorId, namespacePrefix, contextClubId, note))
      actor <- IO(context.support.principal(request.operator))
      updatedAt <- IO.realTimeInstant
      module = context.support.dictionaryModule
      command = UpdateNamespaceContextCommand(
        actor = actor,
        namespacePrefix = request.namespacePrefix,
        contextClubId = request.contextClub,
        note = request.note,
        updatedAt = updatedAt
      )
      registration <- IO(
        updateContext(module, command)
      )
    yield DictionaryNamespaceRegistrationResponse.fromDomain(registration)

  private def updateContext(
      module: riichinexus.bootstrap.DictionaryModuleContext,
      command: UpdateNamespaceContextCommand
  ): DictionaryNamespaceRegistration =
    DictionaryNamespaceManagementOperations.updateContext(
      module = module,
      actor = command.actor,
      namespacePrefix = command.namespacePrefix,
      contextClubId = command.contextClubId,
      note = command.note,
      updatedAt = command.updatedAt
    )

  private final case class UpdateNamespaceContextCommand(
      actor: AccessPrincipal,
      namespacePrefix: String,
      contextClubId: Option[ClubId],
      note: Option[String],
      updatedAt: Instant
  )
