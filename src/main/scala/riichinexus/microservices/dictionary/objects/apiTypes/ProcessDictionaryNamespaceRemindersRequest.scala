package riichinexus.microservices.dictionary.objects.apiTypes

import java.time.Instant

import riichinexus.domain.model.PlayerId
import riichinexus.infrastructure.json.JsonCodecs.given
import upickle.default.*

final case class ProcessDictionaryNamespaceRemindersRequest(
    operatorId: String,
    asOf: Option[String] = None,
    dueSoonHours: Int = 24,
    reminderIntervalHours: Int = 12,
    escalationGraceHours: Int = 72
):
  require(dueSoonHours > 0, "Dictionary namespace dueSoonHours must be positive")
  require(reminderIntervalHours > 0, "Dictionary namespace reminderIntervalHours must be positive")
  require(escalationGraceHours > 0, "Dictionary namespace escalationGraceHours must be positive")

  def operator: PlayerId =
    PlayerId(operatorId)

  def parsedAsOf: Option[Instant] =
    asOf.map(Instant.parse)

object ProcessDictionaryNamespaceRemindersRequest:
  given ReadWriter[ProcessDictionaryNamespaceRemindersRequest] = macroRW
