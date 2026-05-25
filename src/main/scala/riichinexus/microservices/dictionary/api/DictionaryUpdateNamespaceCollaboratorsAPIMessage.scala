package riichinexus.microservices.dictionary.api

import java.time.Instant

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.dictionary.domain.DictionaryNamespaceManagementOperations
import riichinexus.microservices.dictionary.objects.apiTypes.{DictionaryNamespaceRegistration as DictionaryNamespaceRegistrationResponse, *}
import upickle.default.*

final case class DictionaryUpdateNamespaceCollaboratorsAPIMessage(
    operatorId: String,
    namespacePrefix: String,
    coOwnerPlayerIds: Vector[String] = Vector.empty,
    editorPlayerIds: Vector[String] = Vector.empty,
    note: Option[String] = None
) extends APIMessage[DictionaryNamespaceRegistrationResponse] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[DictionaryNamespaceRegistrationResponse] =
    for
      request <- IO(UpdateDictionaryNamespaceCollaboratorsRequest(operatorId, namespacePrefix, coOwnerPlayerIds, editorPlayerIds, note))
      actor <- IO(context.support.principal(request.operator))
      updatedAt <- IO.realTimeInstant
      module = context.support.dictionaryModule
      command = UpdateNamespaceCollaboratorsCommand(
        actor = actor,
        namespacePrefix = request.namespacePrefix,
        coOwnerPlayerIds = request.coOwners,
        editorPlayerIds = request.editors,
        note = request.note,
        updatedAt = updatedAt
      )
      registration <- IO(
        updateCollaborators(module, command)
      )
    yield DictionaryNamespaceRegistrationResponse.fromDomain(registration)

  private def updateCollaborators(
      module: riichinexus.bootstrap.DictionaryModuleContext,
      command: UpdateNamespaceCollaboratorsCommand
  ): DictionaryNamespaceRegistration =
    DictionaryNamespaceManagementOperations.updateCollaborators(
      module = module,
      actor = command.actor,
      namespacePrefix = command.namespacePrefix,
      coOwnerPlayerIds = command.coOwnerPlayerIds,
      editorPlayerIds = command.editorPlayerIds,
      note = command.note,
      updatedAt = command.updatedAt
    )

  private final case class UpdateNamespaceCollaboratorsCommand(
      actor: AccessPrincipal,
      namespacePrefix: String,
      coOwnerPlayerIds: Vector[PlayerId],
      editorPlayerIds: Vector[PlayerId],
      note: Option[String],
      updatedAt: Instant
  )
