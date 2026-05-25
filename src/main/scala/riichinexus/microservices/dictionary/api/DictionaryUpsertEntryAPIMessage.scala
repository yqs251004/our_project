package riichinexus.microservices.dictionary.api

import java.time.Instant

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.dictionary.domain.DictionaryEntryOperations
import riichinexus.microservices.dictionary.objects.apiTypes.{GlobalDictionaryEntry as GlobalDictionaryEntryResponse, *}
import upickle.default.*

final case class DictionaryUpsertEntryAPIMessage(
    operatorId: String,
    key: String,
    value: String,
    note: Option[String] = None
) extends APIMessage[GlobalDictionaryEntryResponse] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[GlobalDictionaryEntryResponse] =
    for
      request <- IO(UpsertDictionaryRequest(operatorId, key, value, note))
      actor <- IO(context.support.principal(request.operator))
      updatedAt <- IO.realTimeInstant
      module = context.support.dictionaryModule
      command = UpsertDictionaryEntryCommand(
        actor = actor,
        key = request.key,
        value = request.value,
        note = request.note,
        updatedAt = updatedAt
      )
      entry <- IO(
        upsertEntry(module, command)
      )
    yield GlobalDictionaryEntryResponse.fromDomain(entry)

  private def upsertEntry(
      module: riichinexus.bootstrap.DictionaryModuleContext,
      command: UpsertDictionaryEntryCommand
  ): GlobalDictionaryEntry =
    DictionaryEntryOperations.upsertEntry(
      module = module,
      actor = command.actor,
      key = command.key,
      value = command.value,
      note = command.note,
      updatedAt = command.updatedAt
    )

  private final case class UpsertDictionaryEntryCommand(
      actor: AccessPrincipal,
      key: String,
      value: String,
      note: Option[String],
      updatedAt: Instant
  )
