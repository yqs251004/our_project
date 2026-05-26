package riichinexus.microservices.dictionary.domain

import java.sql.Connection
import java.time.Instant

import riichinexus.bootstrap.DictionaryModuleContext
import riichinexus.domain.model.*
import riichinexus.domain.service.GlobalDictionaryRegistry
import riichinexus.microservices.dictionary.objects.DictionaryNamespaceRegistration

object DictionaryNamespaceReviewOperations:
  def reviewNamespace(
      connection: Connection,
      module: DictionaryModuleContext,
      actor: AccessPrincipal,
      namespacePrefix: String,
      approve: Boolean,
      note: Option[String],
      reviewedAt: Instant
  ): DictionaryNamespaceRegistration =
    module.transactionManager.inTransaction {
      module.authorizationService.requirePermission(actor, Permission.ManageGlobalDictionary)
      val reviewer = actor.playerId.getOrElse(PlayerId("system"))
      val normalizedPrefix = GlobalDictionaryRegistry.normalizeNamespacePrefix(namespacePrefix)
      val existing = DictionaryNamespaceValidation.requireNamespace(connection, module, normalizedPrefix)
      val reviewed =
        if approve then existing.approve(reviewer, reviewedAt, note)
        else existing.reject(reviewer, reviewedAt, note)

      DictionaryNamespaceAudit.commit(
        module = module,
        aggregate = reviewed,
        eventType = if approve then "DictionaryNamespaceApproved" else "DictionaryNamespaceRejected",
        occurredAt = reviewedAt,
        actorId = actor.playerId,
        details = DictionaryNamespaceAudit.details(existing),
        note = note
      )
    }

  def revokeNamespace(
      connection: Connection,
      module: DictionaryModuleContext,
      actor: AccessPrincipal,
      namespacePrefix: String,
      note: Option[String],
      revokedAt: Instant
  ): DictionaryNamespaceRegistration =
    module.transactionManager.inTransaction {
      module.authorizationService.requirePermission(actor, Permission.ManageGlobalDictionary)
      val reviewer = actor.playerId.getOrElse(PlayerId("system"))
      val normalizedPrefix = GlobalDictionaryRegistry.normalizeNamespacePrefix(namespacePrefix)
      val existing = DictionaryNamespaceValidation.requireNamespace(connection, module, normalizedPrefix)
      DictionaryNamespaceAudit.commit(
        module = module,
        aggregate = existing.revoke(reviewer, revokedAt, note),
        eventType = "DictionaryNamespaceRevoked",
        occurredAt = revokedAt,
        actorId = actor.playerId,
        details = DictionaryNamespaceAudit.details(existing),
        note = note
      )
    }
