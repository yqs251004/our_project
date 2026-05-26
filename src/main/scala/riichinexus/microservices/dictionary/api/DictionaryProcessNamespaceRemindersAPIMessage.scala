package riichinexus.microservices.dictionary.api

import java.time.{Duration, Instant}

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.dictionary.domain.DictionaryNamespaceReminderOperations
import riichinexus.microservices.dictionary.objects.{DictionaryNamespaceReminderAction, DictionaryNamespaceReminderActionView}
import riichinexus.microservices.dictionary.objects.apiTypes.*
import upickle.default.*

final case class DictionaryProcessNamespaceRemindersAPIMessage(
    operatorId: String,
    asOf: Option[String] = None,
    dueSoonHours: Int = 24,
    reminderIntervalHours: Int = 12,
    escalationGraceHours: Int = 72
) extends APIMessage[Vector[DictionaryNamespaceReminderActionView]] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[Vector[DictionaryNamespaceReminderActionView]] =
    for
      request <- IO(ProcessDictionaryNamespaceRemindersRequest(operatorId, asOf, dueSoonHours, reminderIntervalHours, escalationGraceHours))
      actor <- IO(context.principal(request.operator))
      now <- IO.realTimeInstant
      module = context.support.dictionaryModule
      command = ProcessNamespaceRemindersCommand(
        actor = actor,
        asOf = request.parsedAsOf.getOrElse(now),
        dueSoonWindow = Duration.ofHours(request.dueSoonHours.toLong),
        reminderInterval = Duration.ofHours(request.reminderIntervalHours.toLong),
        escalationGrace = Duration.ofHours(request.escalationGraceHours.toLong)
      )
      actions <- IO(
        processReminders(context.connection, module, command)
      )
    yield actions.map(DictionaryNamespaceReminderActionView.fromDomain)

  private def processReminders(
      connection: java.sql.Connection,
      module: riichinexus.bootstrap.DictionaryModuleContext,
      command: ProcessNamespaceRemindersCommand
  ): Vector[DictionaryNamespaceReminderAction] =
    DictionaryNamespaceReminderOperations.processReminders(
      connection = connection,
      module = module,
      actor = command.actor,
      asOf = command.asOf,
      dueSoonWindow = command.dueSoonWindow,
      reminderInterval = command.reminderInterval,
      escalationGrace = command.escalationGrace
    )

  private final case class ProcessNamespaceRemindersCommand(
      actor: AccessPrincipal,
      asOf: Instant,
      dueSoonWindow: Duration,
      reminderInterval: Duration,
      escalationGrace: Duration
  )
