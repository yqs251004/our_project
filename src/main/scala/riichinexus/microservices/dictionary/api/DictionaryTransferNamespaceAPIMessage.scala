package riichinexus.microservices.dictionary.api

import java.time.Instant

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.dictionary.domain.DictionaryNamespaceTransferOperations
import riichinexus.microservices.dictionary.objects.apiTypes.{DictionaryNamespaceRegistration as DictionaryNamespaceRegistrationResponse, *}
import upickle.default.*

final case class DictionaryTransferNamespaceAPIMessage(
    operatorId: String,
    namespacePrefix: String,
    newOwnerPlayerId: String,
    note: Option[String] = None
) extends APIMessage[DictionaryNamespaceRegistrationResponse] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[DictionaryNamespaceRegistrationResponse] =
    for
      request <- IO(TransferDictionaryNamespaceRequest(operatorId, namespacePrefix, newOwnerPlayerId, note))
      actor <- IO(context.support.principal(request.operator))
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
        transferNamespace(module, command)
      )
    yield DictionaryNamespaceRegistrationResponse.fromDomain(registration)

  private def transferNamespace(
      module: riichinexus.bootstrap.DictionaryModuleContext,
      command: TransferNamespaceCommand
  ): DictionaryNamespaceRegistration =
    DictionaryNamespaceTransferOperations.transferNamespace(
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
