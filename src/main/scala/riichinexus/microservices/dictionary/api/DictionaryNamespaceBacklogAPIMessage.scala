package riichinexus.microservices.dictionary.api

import java.time.{Duration, Instant}

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.dictionary.objects.{
  DictionaryNamespaceBacklogView,
  DictionaryNamespaceOwnerBacklog,
  DictionaryNamespaceRegistration,
  DictionaryNamespaceReviewStatus
}
import riichinexus.microservices.dictionary.tables.dictionarynamespace.DictionaryNamespaceTable
import upickle.default.*

final case class DictionaryNamespaceBacklogAPIMessage(
    operatorId: String,
    asOf: Option[String] = None,
    dueSoonHours: Option[Long] = None
) extends APIMessage[DictionaryNamespaceBacklogView] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[DictionaryNamespaceBacklogView] =
    for
      now <- IO.realTimeInstant
      command <- IO(resolveCommand(context, now))
      pending <- IO(listPendingNamespaces(context, command))
    yield buildBacklogView(pending, command)

  private def resolveCommand(context: ApiPlanContext, now: Instant): BacklogCommand =
    val module = context.support.dictionaryModule
    val actor = context.principal(PlayerId(operatorId))
    module.authorizationService.requirePermission(actor, Permission.ManageGlobalDictionary)
    BacklogCommand(
      asOf = asOf.filter(_.nonEmpty).map(Instant.parse).getOrElse(now),
      dueSoonWindow = Duration.ofHours(dueSoonHours.getOrElse(24L))
    )

  private def listPendingNamespaces(
      context: ApiPlanContext,
      command: BacklogCommand
  ): Vector[DictionaryNamespaceRegistration] =
    DictionaryNamespaceTable.findAll(context.connection)
      .filter(_.status == DictionaryNamespaceReviewStatus.Pending)

  private def buildBacklogView(
      pending: Vector[DictionaryNamespaceRegistration],
      command: BacklogCommand
  ): DictionaryNamespaceBacklogView =
    val ownerBacklog = pending
      .groupBy(_.ownerPlayerId)
      .toVector
      .map { case (ownerId, registrations) =>
        DictionaryNamespaceOwnerBacklog(
          ownerPlayerId = ownerId.value,
          pendingCount = registrations.size,
          overdueCount = registrations.count(_.isPendingOverdue(command.asOf)),
          dueSoonCount = registrations.count(_.isPendingDueSoon(command.asOf, command.dueSoonWindow))
        )
      }
      .sortBy(bucket => (-bucket.overdueCount, -bucket.pendingCount, bucket.ownerPlayerId))

    DictionaryNamespaceBacklogView(
      asOf = command.asOf.toString,
      pendingCount = pending.size,
      overdueCount = pending.count(_.isPendingOverdue(command.asOf)),
      dueSoonCount = pending.count(_.isPendingDueSoon(command.asOf, command.dueSoonWindow)),
      oldestPendingRequestedAt = pending.map(_.requestedAt).sorted.headOption.map(_.toString),
      nextDueAt = pending.flatMap(_.reviewDueAt).sorted.headOption.map(_.toString),
      ownerBacklog = ownerBacklog
    )

  private final case class BacklogCommand(
      asOf: Instant,
      dueSoonWindow: Duration
  )
