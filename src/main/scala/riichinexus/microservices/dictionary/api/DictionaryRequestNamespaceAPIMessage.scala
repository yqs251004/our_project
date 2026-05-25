package riichinexus.microservices.dictionary.api

import java.time.Instant

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.dictionary.domain.DictionaryNamespaceRequestOperations
import riichinexus.microservices.dictionary.objects.apiTypes.{DictionaryNamespaceRegistration as DictionaryNamespaceRegistrationResponse, *}
import upickle.default.*

final case class DictionaryRequestNamespaceAPIMessage(
    request: RequestDictionaryNamespaceRequest
) extends APIMessage[DictionaryNamespaceRegistrationResponse] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[DictionaryNamespaceRegistrationResponse] =
    for
      actor <- IO(context.support.principal(request.operator))
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
        requestNamespace(module, command)
      )
    yield DictionaryNamespaceRegistrationResponse.fromDomain(registration)

  private def requestNamespace(
      module: riichinexus.bootstrap.DictionaryModuleContext,
      command: RequestNamespaceCommand
  ): DictionaryNamespaceRegistration =
    DictionaryNamespaceRequestOperations.requestNamespace(
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
