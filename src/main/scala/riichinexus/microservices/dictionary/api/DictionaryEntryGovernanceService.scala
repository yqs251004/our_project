package riichinexus.microservices.dictionary.api

import java.time.Instant

import riichinexus.application.ports.{AuditEventRepository, DomainEventBus, GlobalDictionaryRepository, TransactionManager}
import riichinexus.domain.event.GlobalDictionaryUpdated
import riichinexus.domain.model.*
import riichinexus.domain.service.{AuthorizationService, GlobalDictionaryRegistry}
import riichinexus.microservices.dictionary.tables.DictionaryTables

private[api] final class DictionaryEntryGovernanceService(
    tables: DictionaryTables,
    globalDictionaryRepository: GlobalDictionaryRepository,
    auditEventRepository: AuditEventRepository,
    eventBus: DomainEventBus,
    transactionManager: TransactionManager,
    authorizationService: AuthorizationService,
    policy: DictionaryNamespacePolicySupport
):
  private def approvedMetadataNamespaceForKey(key: String): Option[DictionaryNamespaceRegistration] =
    val normalizedKey = GlobalDictionaryRegistry.normalizeKey(key)
    tables.listApprovedNamespaces()
      .filter(registration => normalizedKey.startsWith(registration.namespacePrefix))
      .sortBy(_.namespacePrefix.length)
      .lastOption

  def upsertDictionary(
      key: String,
      value: String,
      actor: AccessPrincipal,
      note: Option[String] = None,
      updatedAt: Instant = Instant.now()
  ): GlobalDictionaryEntry =
    transactionManager.inTransaction {
      require(key.trim.nonEmpty, "Dictionary key cannot be empty")
      require(value.trim.nonEmpty, "Dictionary value cannot be empty")
      GlobalDictionaryRegistry.validate(key, value)

      if GlobalDictionaryRegistry.isMetadataKey(key) then
        val namespace = approvedMetadataNamespaceForKey(key).getOrElse(
          throw IllegalArgumentException(
            s"Metadata key $key requires an approved namespace registration such as ${GlobalDictionaryRegistry.metadataNamespacePrefixForKey(key)}"
          )
        )
        val actorId = actor.playerId.getOrElse(
          throw IllegalArgumentException("Metadata dictionary writes require a registered player identity")
        )
        if !actor.isSuperAdmin then
          policy.requireNamespaceWriterActor(actorId, namespace, s"write ${key.trim}")
      else
        authorizationService.requirePermission(actor, Permission.ManageGlobalDictionary)

      val existingVersion = tables.findEntryByKey(key).map(_.version).getOrElse(0)
      val entry = GlobalDictionaryEntry(
        key = key,
        value = value,
        updatedAt = updatedAt,
        updatedBy = actor.playerId.getOrElse(PlayerId("system")),
        note = note,
        version = existingVersion
      )

      val saved = globalDictionaryRepository.save(entry)
      auditEventRepository.save(
        AuditEventEntry(
          id = IdGenerator.auditEventId(),
          aggregateType = "dictionary",
          aggregateId = key,
          eventType = "GlobalDictionaryUpserted",
          occurredAt = updatedAt,
          actorId = Some(entry.updatedBy),
          details = Map("key" -> key, "value" -> value),
          note = note
        )
      )
      eventBus.publish(GlobalDictionaryUpdated(saved, updatedAt))
      saved
    }
