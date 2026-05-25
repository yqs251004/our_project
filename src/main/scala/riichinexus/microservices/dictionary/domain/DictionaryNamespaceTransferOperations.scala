package riichinexus.microservices.dictionary.domain

import java.time.Instant

import riichinexus.bootstrap.DictionaryModuleContext
import riichinexus.domain.model.*
import riichinexus.domain.service.GlobalDictionaryRegistry

object DictionaryNamespaceTransferOperations:
  def transferNamespace(
      module: DictionaryModuleContext,
      actor: AccessPrincipal,
      namespacePrefix: String,
      newOwnerPlayerId: PlayerId,
      note: Option[String],
      transferredAt: Instant
  ): DictionaryNamespaceRegistration =
    module.transactionManager.inTransaction {
      module.authorizationService.requirePermission(actor, Permission.ManageGlobalDictionary)
      val reviewer = actor.playerId.getOrElse(PlayerId("system"))
      val newOwner = DictionaryNamespaceValidation.requireActiveOwner(
        module,
        newOwnerPlayerId,
        s"transfer ownership to ${newOwnerPlayerId.value}"
      )
      val normalizedPrefix = GlobalDictionaryRegistry.normalizeNamespacePrefix(namespacePrefix)
      val existing = DictionaryNamespaceValidation.requireNamespace(module, normalizedPrefix)
      existing.contextClubId.foreach { clubId =>
        DictionaryNamespaceValidation.requireContextMembership(
          newOwner,
          clubId,
          s"transfer ownership for $normalizedPrefix to ${newOwnerPlayerId.value}"
        )
      }
      val transferred = existing.transferOwnership(newOwnerPlayerId, reviewer, transferredAt, note)

      DictionaryNamespaceAudit.commit(
        module = module,
        aggregate = transferred,
        eventType = "DictionaryNamespaceTransferred",
        occurredAt = transferredAt,
        actorId = actor.playerId,
        details = DictionaryNamespaceAudit.details(
          contextClubId = existing.contextClubId,
          ownerPlayerId = newOwnerPlayerId,
          coOwnerPlayerIds = transferred.coOwnerPlayerIds,
          editorPlayerIds = transferred.editorPlayerIds
        ).updated("previousOwnerPlayerId", existing.ownerPlayerId.value),
        note = note
      )
    }
