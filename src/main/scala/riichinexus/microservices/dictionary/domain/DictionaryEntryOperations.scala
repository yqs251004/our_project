package riichinexus.microservices.dictionary.domain

import java.time.Instant

import riichinexus.application.changes.{DomainChange, DomainChangeInterpreter}
import riichinexus.bootstrap.DictionaryModuleContext
import riichinexus.domain.event.GlobalDictionaryUpdated
import riichinexus.domain.model.*
import riichinexus.domain.service.GlobalDictionaryRegistry

object DictionaryEntryOperations:
  def upsertEntry(
      module: DictionaryModuleContext,
      actor: AccessPrincipal,
      key: String,
      value: String,
      note: Option[String],
      updatedAt: Instant
  ): GlobalDictionaryEntry =
    module.transactionManager.inTransaction {
      require(key.trim.nonEmpty, "Dictionary key cannot be empty")
      require(value.trim.nonEmpty, "Dictionary value cannot be empty")
      GlobalDictionaryRegistry.validate(key, value)
      requireWriteAccess(module, actor, key)

      val existingVersion = module.tables.findEntryByKey(key).map(_.version).getOrElse(0)
      val entry = GlobalDictionaryEntry(
        key = key,
        value = value,
        updatedAt = updatedAt,
        updatedBy = actor.playerId.getOrElse(PlayerId("system")),
        note = note,
        version = existingVersion
      )

      DomainChangeInterpreter
        .auditAndEvents(module.transactionManager, module.auditEventRepository, module.eventBus)
        .commitWithinTransaction(
          DomainChange(
            aggregate = entry,
            persist = module.globalDictionaryRepository.save,
            auditEntries = savedEntry =>
              Vector(
                AuditEventEntry(
                  id = IdGenerator.auditEventId(),
                  aggregateType = "dictionary",
                  aggregateId = key,
                  eventType = "GlobalDictionaryUpserted",
                  occurredAt = updatedAt,
                  actorId = Some(savedEntry.updatedBy),
                  details = Map("key" -> key, "value" -> value),
                  note = note
                )
              ),
            domainEvents = savedEntry => Vector(GlobalDictionaryUpdated(savedEntry, updatedAt))
          )
        )
    }

  private def requireWriteAccess(
      module: DictionaryModuleContext,
      actor: AccessPrincipal,
      key: String
  ): Unit =
    if GlobalDictionaryRegistry.isMetadataKey(key) then
      val namespace = approvedMetadataNamespaceForKey(module, key).getOrElse(
        throw IllegalArgumentException(
          s"Metadata key $key requires an approved namespace registration such as ${GlobalDictionaryRegistry.metadataNamespacePrefixForKey(key)}"
        )
      )
      val actorId = actor.playerId.getOrElse(
        throw IllegalArgumentException("Metadata dictionary writes require a registered player identity")
      )
      if !actor.isSuperAdmin then
        requireNamespaceWriterActor(module, actorId, namespace, s"write ${key.trim}")
    else
      module.authorizationService.requirePermission(actor, Permission.ManageGlobalDictionary)

  private def approvedMetadataNamespaceForKey(
      module: DictionaryModuleContext,
      key: String
  ): Option[DictionaryNamespaceRegistration] =
    val normalizedKey = GlobalDictionaryRegistry.normalizeKey(key)
    module.tables.listApprovedNamespaces()
      .filter(registration => normalizedKey.startsWith(registration.namespacePrefix))
      .sortBy(_.namespacePrefix.length)
      .lastOption

  private def requireNamespaceWriterActor(
      module: DictionaryModuleContext,
      actorId: PlayerId,
      registration: DictionaryNamespaceRegistration,
      action: String
  ): Unit =
    if !registration.hasWriteAccess(actorId) then
      throw IllegalArgumentException(
        s"Metadata namespace ${registration.namespacePrefix} is writable only by its owners/editors"
      )
    registration.contextClubId.foreach { clubId =>
      val player = DictionaryNamespaceValidation.requireActiveOwner(module, actorId, s"$action writer ${actorId.value}")
      if !player.boundClubIds.contains(clubId) then
        throw IllegalArgumentException(
          s"Dictionary namespace $action writer ${actorId.value} requires ${actorId.value} to belong to context club ${clubId.value}"
        )
    }
