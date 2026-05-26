package riichinexus.microservices.dictionary.api

import java.time.Instant

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.dictionary.domain.DictionaryEntryOperations
import riichinexus.microservices.dictionary.objects.{GlobalDictionaryEntry, GlobalDictionaryEntryView}
import riichinexus.microservices.dictionary.objects.apiTypes.*
import upickle.default.*

final case class DictionaryUpsertEntryAPIMessage(
    operatorId: String,
    key: String,
    value: String,
    note: Option[String] = None
) extends APIMessage[GlobalDictionaryEntryView] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[GlobalDictionaryEntryView] =
    for
      request <- IO(UpsertDictionaryRequest(operatorId, key, value, note))
      actor <- IO(context.principal(request.operator))
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
        upsertEntry(context.connection, module, command)
      )
    yield GlobalDictionaryEntryView.fromDomain(entry)

  private def upsertEntry(
      connection: java.sql.Connection,
      module: riichinexus.bootstrap.DictionaryModuleContext,
      command: UpsertDictionaryEntryCommand
  ): GlobalDictionaryEntry =
    DictionaryEntryOperations.upsertEntry(
      connection = connection,
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
