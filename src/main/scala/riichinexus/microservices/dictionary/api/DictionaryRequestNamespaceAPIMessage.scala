package riichinexus.microservices.dictionary.api

import java.time.Instant

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.dictionary.domain.DictionaryNamespaceRequestOperations
import riichinexus.microservices.dictionary.objects.{DictionaryNamespaceRegistration, DictionaryNamespaceRegistrationView}
import riichinexus.microservices.dictionary.objects.apiTypes.*
import upickle.default.*

final case class DictionaryRequestNamespaceAPIMessage(
    request: RequestDictionaryNamespaceRequest
) extends APIMessage[DictionaryNamespaceRegistrationView] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[DictionaryNamespaceRegistrationView] =
    for
      actor <- IO(context.principal(request.operator))
      requestedAt <- IO.realTimeInstant
      module = context.support.dictionaryModule
      command = RequestNamespaceCommand(
        actor = actor,
        namespacePrefix = request.namespacePrefix,
        ownerPlayerId = request.owner,
        coOwnerPlayerIds = request.coOwners,
        editorPlayerIds = request.editors,
        contextClubId = request.contextClub,
        reviewDueAt = request.parsedReviewDueAt,
        note = request.note,
        requestedAt = requestedAt
      )
      registration <- IO(
        requestNamespace(context.connection, module, command)
      )
    yield DictionaryNamespaceRegistrationView.fromDomain(registration)

  private def requestNamespace(
      connection: java.sql.Connection,
      module: riichinexus.bootstrap.DictionaryModuleContext,
      command: RequestNamespaceCommand
  ): DictionaryNamespaceRegistration =
    DictionaryNamespaceRequestOperations.requestNamespace(
      connection = connection,
      module = module,
      actor = command.actor,
      namespacePrefix = command.namespacePrefix,
      ownerPlayerId = command.ownerPlayerId,
      coOwnerPlayerIds = command.coOwnerPlayerIds,
      editorPlayerIds = command.editorPlayerIds,
      contextClubId = command.contextClubId,
      reviewDueAt = command.reviewDueAt,
      note = command.note,
      requestedAt = command.requestedAt
    )

  private final case class RequestNamespaceCommand(
      actor: AccessPrincipal,
      namespacePrefix: String,
      ownerPlayerId: Option[PlayerId],
      coOwnerPlayerIds: Vector[PlayerId],
      editorPlayerIds: Vector[PlayerId],
      contextClubId: Option[ClubId],
      reviewDueAt: Option[Instant],
      note: Option[String],
      requestedAt: Instant
  )
