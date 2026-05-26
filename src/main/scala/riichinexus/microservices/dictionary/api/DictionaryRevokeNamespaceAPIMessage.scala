package riichinexus.microservices.dictionary.api

import java.time.Instant

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.dictionary.domain.DictionaryNamespaceReviewOperations
import riichinexus.microservices.dictionary.objects.{DictionaryNamespaceRegistration, DictionaryNamespaceRegistrationView}
import riichinexus.microservices.dictionary.objects.apiTypes.*
import upickle.default.*

final case class DictionaryRevokeNamespaceAPIMessage(
    operatorId: String,
    namespacePrefix: String,
    note: Option[String] = None
) extends APIMessage[DictionaryNamespaceRegistrationView] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[DictionaryNamespaceRegistrationView] =
    for
      request <- IO(RevokeDictionaryNamespaceRequest(operatorId, namespacePrefix, note))
      actor <- IO(context.principal(request.operator))
      revokedAt <- IO.realTimeInstant
      module = context.support.dictionaryModule
      command = RevokeNamespaceCommand(
        actor = actor,
        namespacePrefix = request.namespacePrefix,
        note = request.note,
        revokedAt = revokedAt
      )
      registration <- IO(
        revokeNamespace(context.connection, module, command)
      )
    yield DictionaryNamespaceRegistrationView.fromDomain(registration)

  private def revokeNamespace(
      connection: java.sql.Connection,
      module: riichinexus.bootstrap.DictionaryModuleContext,
      command: RevokeNamespaceCommand
  ): DictionaryNamespaceRegistration =
    DictionaryNamespaceReviewOperations.revokeNamespace(
      connection = connection,
      module = module,
      actor = command.actor,
      namespacePrefix = command.namespacePrefix,
      note = command.note,
      revokedAt = command.revokedAt
    )

  private final case class RevokeNamespaceCommand(
      actor: AccessPrincipal,
      namespacePrefix: String,
      note: Option[String],
      revokedAt: Instant
  )
