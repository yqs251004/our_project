package riichinexus.microservices.dictionary.api

import java.time.{Duration, Instant}

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.dictionary.domain.DictionaryNamespaceReminderOperations
import riichinexus.microservices.dictionary.objects.apiTypes.{DictionaryNamespaceReminderAction as DictionaryNamespaceReminderActionResponse, *}
import upickle.default.*

final case class DictionaryProcessNamespaceRemindersAPIMessage(
    operatorId: String,
    asOf: Option[String] = None,
    dueSoonHours: Int = 24,
    reminderIntervalHours: Int = 12,
    escalationGraceHours: Int = 72
) extends APIMessage[Vector[DictionaryNamespaceReminderActionResponse]] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[Vector[DictionaryNamespaceReminderActionResponse]] =
    for
      request <- IO(ProcessDictionaryNamespaceRemindersRequest(operatorId, asOf, dueSoonHours, reminderIntervalHours, escalationGraceHours))
      actor <- IO(context.support.principal(request.operator))
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
        processReminders(module, command)
      )
    yield actions.map(DictionaryNamespaceReminderActionResponse.fromDomain)

  private def processReminders(
      module: riichinexus.bootstrap.DictionaryModuleContext,
      command: ProcessNamespaceRemindersCommand
  ): Vector[DictionaryNamespaceReminderAction] =
    DictionaryNamespaceReminderOperations.processReminders(
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
