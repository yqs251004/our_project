package riichinexus.microservices.dictionary.api

import java.time.Instant

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.dictionary.domain.DictionaryNamespaceTransferOperations
import riichinexus.microservices.dictionary.objects.{DictionaryNamespaceRegistration, DictionaryNamespaceRegistrationView}
import riichinexus.microservices.dictionary.objects.apiTypes.*
import upickle.default.*

final case class DictionaryTransferNamespaceAPIMessage(
    operatorId: String,
    namespacePrefix: String,
    newOwnerPlayerId: String,
    note: Option[String] = None
) extends APIMessage[DictionaryNamespaceRegistrationView] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[DictionaryNamespaceRegistrationView] =
    for
      request <- IO(TransferDictionaryNamespaceRequest(operatorId, namespacePrefix, newOwnerPlayerId, note))
      actor <- IO(context.principal(request.operator))
      transferredAt <- IO.realTimeInstant
      module = context.support.dictionaryModule
      command = TransferNamespaceCommand(
        actor = actor,
        namespacePrefix = request.namespacePrefix,
        newOwnerPlayerId = request.newOwner,
        note = request.note,
        transferredAt = transferredAt
      )
      registration <- IO(
        transferNamespace(context.connection, module, command)
      )
    yield DictionaryNamespaceRegistrationView.fromDomain(registration)

  private def transferNamespace(
      connection: java.sql.Connection,
      module: riichinexus.bootstrap.DictionaryModuleContext,
      command: TransferNamespaceCommand
  ): DictionaryNamespaceRegistration =
    DictionaryNamespaceTransferOperations.transferNamespace(
      connection = connection,
      module = module,
      actor = command.actor,
      namespacePrefix = command.namespacePrefix,
      newOwnerPlayerId = command.newOwnerPlayerId,
      note = command.note,
      transferredAt = command.transferredAt
    )

  private final case class TransferNamespaceCommand(
      actor: AccessPrincipal,
      namespacePrefix: String,
      newOwnerPlayerId: PlayerId,
      note: Option[String],
      transferredAt: Instant
  )
