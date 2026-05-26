package riichinexus.microservices.dictionary.api

import java.time.Instant

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.dictionary.domain.DictionaryNamespaceManagementOperations
import riichinexus.microservices.dictionary.objects.{DictionaryNamespaceRegistration, DictionaryNamespaceRegistrationView}
import riichinexus.microservices.dictionary.objects.apiTypes.*
import upickle.default.*

final case class DictionaryUpdateNamespaceCollaboratorsAPIMessage(
    operatorId: String,
    namespacePrefix: String,
    coOwnerPlayerIds: Vector[String] = Vector.empty,
    editorPlayerIds: Vector[String] = Vector.empty,
    note: Option[String] = None
) extends APIMessage[DictionaryNamespaceRegistrationView] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[DictionaryNamespaceRegistrationView] =
    for
      request <- IO(UpdateDictionaryNamespaceCollaboratorsRequest(operatorId, namespacePrefix, coOwnerPlayerIds, editorPlayerIds, note))
      actor <- IO(context.principal(request.operator))
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
        updateCollaborators(context.connection, module, command)
      )
    yield DictionaryNamespaceRegistrationView.fromDomain(registration)

  private def updateCollaborators(
      connection: java.sql.Connection,
      module: riichinexus.bootstrap.DictionaryModuleContext,
      command: UpdateNamespaceCollaboratorsCommand
  ): DictionaryNamespaceRegistration =
    DictionaryNamespaceManagementOperations.updateCollaborators(
      connection = connection,
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
