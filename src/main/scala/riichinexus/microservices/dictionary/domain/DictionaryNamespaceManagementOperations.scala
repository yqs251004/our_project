package riichinexus.microservices.dictionary.domain

import java.sql.Connection
import java.time.Instant

import riichinexus.bootstrap.DictionaryModuleContext
import riichinexus.domain.model.*
import riichinexus.domain.service.GlobalDictionaryRegistry
import riichinexus.microservices.dictionary.objects.DictionaryNamespaceRegistration

object DictionaryNamespaceManagementOperations:
  def updateCollaborators(
      connection: Connection,
      module: DictionaryModuleContext,
      actor: AccessPrincipal,
      namespacePrefix: String,
      coOwnerPlayerIds: Vector[PlayerId],
      editorPlayerIds: Vector[PlayerId],
      note: Option[String],
      updatedAt: Instant
  ): DictionaryNamespaceRegistration =
    module.transactionManager.inTransaction {
      val normalizedPrefix = GlobalDictionaryRegistry.normalizeNamespacePrefix(namespacePrefix)
      val existing = DictionaryNamespaceValidation.requireNamespace(connection, module, normalizedPrefix)
      val reviewer = DictionaryNamespaceValidation.requireManagementActor(
        actor,
        existing,
        s"update collaborators for $normalizedPrefix"
      )
      val (normalizedCoOwners, normalizedEditors) = DictionaryNamespaceValidation.normalizeCollaborators(
        connection,
        module,
        existing.ownerPlayerId,
        coOwnerPlayerIds,
        editorPlayerIds,
        s"update collaborators for $normalizedPrefix"
      )
      DictionaryNamespaceValidation.validateContextMembership(
        connection,
        module,
        existing.contextClubId,
        DictionaryNamespaceValidation.requireActiveOwner(
          connection,
          module,
          existing.ownerPlayerId,
          s"update collaborators for $normalizedPrefix owner ${existing.ownerPlayerId.value}"
        ),
        normalizedCoOwners,
        normalizedEditors,
        s"update collaborators for $normalizedPrefix"
      )

      DictionaryNamespaceAudit.commit(
        module = module,
        aggregate = existing.updateCollaborators(normalizedCoOwners, normalizedEditors, reviewer, updatedAt, note),
        eventType = "DictionaryNamespaceCollaboratorsUpdated",
        occurredAt = updatedAt,
        actorId = actor.playerId,
        details = DictionaryNamespaceAudit.details(
          contextClubId = existing.contextClubId,
          ownerPlayerId = existing.ownerPlayerId,
          coOwnerPlayerIds = normalizedCoOwners,
          editorPlayerIds = normalizedEditors
        ),
        note = note
      )
    }

  def updateContext(
      connection: Connection,
      module: DictionaryModuleContext,
      actor: AccessPrincipal,
      namespacePrefix: String,
      contextClubId: Option[ClubId],
      note: Option[String],
      updatedAt: Instant
  ): DictionaryNamespaceRegistration =
    module.transactionManager.inTransaction {
      val normalizedPrefix = GlobalDictionaryRegistry.normalizeNamespacePrefix(namespacePrefix)
      val existing = DictionaryNamespaceValidation.requireNamespace(connection, module, normalizedPrefix)
      val reviewer = DictionaryNamespaceValidation.requireManagementActor(actor, existing, s"update context for $normalizedPrefix")
      val normalizedContextClubId = DictionaryNamespaceValidation.validateContextMembership(
        connection,
        module,
        contextClubId,
        DictionaryNamespaceValidation.requireActiveOwner(
          connection,
          module,
          existing.ownerPlayerId,
          s"update context for $normalizedPrefix owner ${existing.ownerPlayerId.value}"
        ),
        existing.coOwnerPlayerIds,
        existing.editorPlayerIds,
        s"update context for $normalizedPrefix"
      )

      DictionaryNamespaceAudit.commit(
        module = module,
        aggregate = existing.updateContextClub(normalizedContextClubId, reviewer, updatedAt, note),
        eventType = "DictionaryNamespaceContextUpdated",
        occurredAt = updatedAt,
        actorId = actor.playerId,
        details = DictionaryNamespaceAudit.details(
          contextClubId = normalizedContextClubId,
          ownerPlayerId = existing.ownerPlayerId,
          coOwnerPlayerIds = existing.coOwnerPlayerIds,
          editorPlayerIds = existing.editorPlayerIds
        ).updated("previousContextClubId", existing.contextClubId.map(_.value).getOrElse("")),
        note = note
      )
    }
