package riichinexus.microservices.dictionary.api

import java.time.Instant

import riichinexus.domain.service.GlobalDictionaryRegistry
import riichinexus.domain.model.*
import riichinexus.microservices.dictionary.api.requests.UpsertDictionaryRequest
import riichinexus.microservices.dictionary.api.responses.GlobalDictionarySchemaView
import riichinexus.microservices.dictionary.objects.DictionaryEntryQuery
import riichinexus.microservices.dictionary.tables.DictionaryTables

object DictionaryEntryApi:

  def listEntries(
      tables: DictionaryTables,
      query: DictionaryEntryQuery
  ): Vector[GlobalDictionaryEntry] =
    tables.listEntries()
      .filter(entry => query.prefix.forall(prefix => entry.key.startsWith(prefix)))
      .filter(entry => query.updatedBy.forall(_ == entry.updatedBy))
      .sortBy(_.key)

  def schemaView: GlobalDictionarySchemaView =
    val schema = GlobalDictionaryRegistry.schemaView
    GlobalDictionarySchemaView(
      entries = schema.entries,
      unknownKeyPolicy = schema.unknownKeyPolicy
    )

  def findByKey(
      tables: DictionaryTables,
      key: String
  ): Option[GlobalDictionaryEntry] =
    tables.findEntryByKey(key)

  def upsert(
      governance: DictionaryGovernanceService,
      actor: AccessPrincipal,
      request: UpsertDictionaryRequest,
      updatedAt: Instant = Instant.now()
  ): GlobalDictionaryEntry =
    governance.upsertDictionary(
      key = request.key,
      value = request.value,
      actor = actor,
      note = request.note,
      updatedAt = updatedAt
    )
