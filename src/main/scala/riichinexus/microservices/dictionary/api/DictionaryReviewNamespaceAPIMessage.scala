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

final case class DictionaryReviewNamespaceAPIMessage(
    operatorId: String,
    namespacePrefix: String,
    approve: Boolean,
    note: Option[String] = None
) extends APIMessage[DictionaryNamespaceRegistrationView] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[DictionaryNamespaceRegistrationView] =
    for
      request <- IO(ReviewDictionaryNamespaceRequest(operatorId, namespacePrefix, approve, note))
      actor <- IO(context.principal(request.operator))
      reviewedAt <- IO.realTimeInstant
      module = context.support.dictionaryModule
      command = ReviewNamespaceCommand(
        actor = actor,
        namespacePrefix = request.namespacePrefix,
        approve = request.approve,
        note = request.note,
        reviewedAt = reviewedAt
      )
      registration <- IO(
        reviewNamespace(context.connection, module, command)
      )
    yield DictionaryNamespaceRegistrationView.fromDomain(registration)

  private def reviewNamespace(
      connection: java.sql.Connection,
      module: riichinexus.bootstrap.DictionaryModuleContext,
      command: ReviewNamespaceCommand
  ): DictionaryNamespaceRegistration =
    DictionaryNamespaceReviewOperations.reviewNamespace(
      connection = connection,
      module = module,
      actor = command.actor,
      namespacePrefix = command.namespacePrefix,
      approve = command.approve,
      note = command.note,
      reviewedAt = command.reviewedAt
    )

  private final case class ReviewNamespaceCommand(
      actor: AccessPrincipal,
      namespacePrefix: String,
      approve: Boolean,
      note: Option[String],
      reviewedAt: Instant
  )
