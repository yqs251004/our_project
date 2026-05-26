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

final case class DictionaryUpdateNamespaceContextAPIMessage(
    operatorId: String,
    namespacePrefix: String,
    contextClubId: Option[String] = None,
    note: Option[String] = None
) extends APIMessage[DictionaryNamespaceRegistrationView] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[DictionaryNamespaceRegistrationView] =
    for
      request <- IO(UpdateDictionaryNamespaceContextRequest(operatorId, namespacePrefix, contextClubId, note))
      actor <- IO(context.principal(request.operator))
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
        updateContext(context.connection, module, command)
      )
    yield DictionaryNamespaceRegistrationView.fromDomain(registration)

  private def updateContext(
      connection: java.sql.Connection,
      module: riichinexus.bootstrap.DictionaryModuleContext,
      command: UpdateNamespaceContextCommand
  ): DictionaryNamespaceRegistration =
    DictionaryNamespaceManagementOperations.updateContext(
      connection = connection,
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
