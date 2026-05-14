package riichinexus.microservices.dictionary.api

import java.time.Instant

import riichinexus.application.ports.{AuditEventRepository, DictionaryNamespaceRepository, TransactionManager}
import riichinexus.domain.model.*
import riichinexus.domain.service.{AuthorizationService, GlobalDictionaryRegistry}
import riichinexus.microservices.dictionary.tables.DictionaryTables

private[api] final class DictionaryNamespaceGovernanceService(
    tables: DictionaryTables,
    dictionaryNamespaceRepository: DictionaryNamespaceRepository,
    auditEventRepository: AuditEventRepository,
    transactionManager: TransactionManager,
    authorizationService: AuthorizationService,
    policy: DictionaryNamespacePolicySupport
):
  def requestDictionaryNamespace(
      namespacePrefix: String,
      actor: AccessPrincipal,
      contextClubId: Option[ClubId] = None,
      ownerPlayerId: Option[PlayerId] = None,
      coOwnerPlayerIds: Vector[PlayerId] = Vector.empty,
      editorPlayerIds: Vector[PlayerId] = Vector.empty,
      note: Option[String] = None,
      requestedAt: Instant = Instant.now(),
      reviewDueAt: Option[Instant] = None
  ): DictionaryNamespaceRegistration =
    transactionManager.inTransaction {
      val requesterId = actor.playerId.getOrElse(
        throw IllegalArgumentException("Dictionary namespace requests require a registered player identity")
      )
      val effectiveOwner = ownerPlayerId.getOrElse(requesterId)
      if effectiveOwner != requesterId && !actor.isSuperAdmin then
        throw IllegalArgumentException("Only super admins can request a namespace on behalf of another owner")
      val owner = policy.requireActiveNamespaceOwner(effectiveOwner, s"request ownership for ${effectiveOwner.value}")
      val (normalizedCoOwners, normalizedEditors) = policy.normalizeNamespaceCollaborators(
        effectiveOwner,
        coOwnerPlayerIds,
        editorPlayerIds,
        s"request ${namespacePrefix.trim}"
      )

      val normalizedPrefix = GlobalDictionaryRegistry.normalizeNamespacePrefix(namespacePrefix)
      val effectiveReviewDueAt = reviewDueAt.orElse(Some(requestedAt.plus(java.time.Duration.ofHours(72))))
      require(
        effectiveReviewDueAt.forall(!_.isBefore(requestedAt)),
        "Dictionary namespace reviewDueAt cannot be earlier than requestedAt"
      )
      val normalizedContextClubId = policy.validateNamespaceContextMembership(
        contextClubId,
        owner,
        normalizedCoOwners,
        normalizedEditors,
        s"request ${normalizedPrefix.trim}"
      )
      tables.findNamespaceByPrefix(normalizedPrefix) match
        case Some(existing)
            if existing.status == DictionaryNamespaceReviewStatus.Approved &&
              existing.ownerPlayerId == effectiveOwner &&
              existing.coOwnerPlayerIds == normalizedCoOwners &&
              existing.editorPlayerIds == normalizedEditors &&
              existing.contextClubId == normalizedContextClubId =>
          existing
        case Some(existing) if existing.status == DictionaryNamespaceReviewStatus.Approved =>
          throw IllegalArgumentException(s"Dictionary namespace $normalizedPrefix is already owned by ${existing.ownerPlayerId.value}")
        case Some(existing) if existing.status == DictionaryNamespaceReviewStatus.Pending =>
          throw IllegalArgumentException(s"Dictionary namespace $normalizedPrefix already has a pending review")
        case _ =>
          val registration = dictionaryNamespaceRepository.save(
            DictionaryNamespaceRegistration(
              namespacePrefix = normalizedPrefix,
              contextClubId = normalizedContextClubId,
              ownerPlayerId = effectiveOwner,
              coOwnerPlayerIds = normalizedCoOwners,
              editorPlayerIds = normalizedEditors,
              requestedBy = requesterId,
              requestedAt = requestedAt,
              reviewDueAt = effectiveReviewDueAt,
              reviewNote = note
            )
          )
          auditEventRepository.save(
            AuditEventEntry(
              id = IdGenerator.auditEventId(),
              aggregateType = "dictionary-namespace",
              aggregateId = normalizedPrefix,
              eventType = "DictionaryNamespaceRequested",
              occurredAt = requestedAt,
              actorId = Some(requesterId),
              details = Map(
                "contextClubId" -> normalizedContextClubId.map(_.value).getOrElse(""),
                "ownerPlayerId" -> effectiveOwner.value,
                "coOwnerPlayerIds" -> normalizedCoOwners.map(_.value).mkString(","),
                "editorPlayerIds" -> normalizedEditors.map(_.value).mkString(","),
                "reviewDueAt" -> effectiveReviewDueAt.map(_.toString).getOrElse("")
              ),
              note = note
            )
          )
          registration
    }

  def reviewDictionaryNamespace(
      namespacePrefix: String,
      approve: Boolean,
      actor: AccessPrincipal,
      note: Option[String] = None,
      reviewedAt: Instant = Instant.now()
  ): Option[DictionaryNamespaceRegistration] =
    transactionManager.inTransaction {
      authorizationService.requirePermission(actor, Permission.ManageGlobalDictionary)
      val reviewer = actor.playerId.getOrElse(PlayerId("system"))
      val normalizedPrefix = GlobalDictionaryRegistry.normalizeNamespacePrefix(namespacePrefix)
      tables.findNamespaceByPrefix(normalizedPrefix).map { existing =>
        val reviewed =
          if approve then existing.approve(reviewer, reviewedAt, note)
          else existing.reject(reviewer, reviewedAt, note)

        dictionaryNamespaceRepository.save(reviewed)
        auditEventRepository.save(
          AuditEventEntry(
            id = IdGenerator.auditEventId(),
            aggregateType = "dictionary-namespace",
            aggregateId = normalizedPrefix,
            eventType = if approve then "DictionaryNamespaceApproved" else "DictionaryNamespaceRejected",
            occurredAt = reviewedAt,
            actorId = actor.playerId,
            details = Map(
              "contextClubId" -> existing.contextClubId.map(_.value).getOrElse(""),
              "ownerPlayerId" -> existing.ownerPlayerId.value,
              "coOwnerPlayerIds" -> existing.coOwnerPlayerIds.map(_.value).mkString(","),
              "editorPlayerIds" -> existing.editorPlayerIds.map(_.value).mkString(",")
            ),
            note = note
          )
        )
        reviewed
      }
    }

  def updateDictionaryNamespaceCollaborators(
      namespacePrefix: String,
      coOwnerPlayerIds: Vector[PlayerId],
      editorPlayerIds: Vector[PlayerId],
      actor: AccessPrincipal,
      note: Option[String] = None,
      updatedAt: Instant = Instant.now()
  ): Option[DictionaryNamespaceRegistration] =
    transactionManager.inTransaction {
      val normalizedPrefix = GlobalDictionaryRegistry.normalizeNamespacePrefix(namespacePrefix)
      tables.findNamespaceByPrefix(normalizedPrefix).map { existing =>
        val reviewer = policy.requireNamespaceManagementActor(actor, existing, s"update collaborators for $normalizedPrefix")
        val (normalizedCoOwners, normalizedEditors) = policy.normalizeNamespaceCollaborators(
          existing.ownerPlayerId,
          coOwnerPlayerIds,
          editorPlayerIds,
          s"update collaborators for $normalizedPrefix"
        )
        policy.validateNamespaceContextMembership(
          existing.contextClubId,
          policy.requireActiveNamespaceOwner(existing.ownerPlayerId, s"update collaborators for $normalizedPrefix owner ${existing.ownerPlayerId.value}"),
          normalizedCoOwners,
          normalizedEditors,
          s"update collaborators for $normalizedPrefix"
        )
        val updated = existing.updateCollaborators(normalizedCoOwners, normalizedEditors, reviewer, updatedAt, note)
        dictionaryNamespaceRepository.save(updated)
        auditEventRepository.save(
          AuditEventEntry(
            id = IdGenerator.auditEventId(),
            aggregateType = "dictionary-namespace",
            aggregateId = normalizedPrefix,
            eventType = "DictionaryNamespaceCollaboratorsUpdated",
            occurredAt = updatedAt,
            actorId = actor.playerId,
            details = Map(
              "contextClubId" -> existing.contextClubId.map(_.value).getOrElse(""),
              "ownerPlayerId" -> existing.ownerPlayerId.value,
              "coOwnerPlayerIds" -> normalizedCoOwners.map(_.value).mkString(","),
              "editorPlayerIds" -> normalizedEditors.map(_.value).mkString(",")
            ),
            note = note
          )
        )
        updated
      }
    }

  def transferDictionaryNamespace(
      namespacePrefix: String,
      newOwnerId: PlayerId,
      actor: AccessPrincipal,
      note: Option[String] = None,
      transferredAt: Instant = Instant.now()
  ): Option[DictionaryNamespaceRegistration] =
    transactionManager.inTransaction {
      authorizationService.requirePermission(actor, Permission.ManageGlobalDictionary)
      val reviewer = actor.playerId.getOrElse(PlayerId("system"))
      val newOwner = policy.requireActiveNamespaceOwner(newOwnerId, s"transfer ownership to ${newOwnerId.value}")
      val normalizedPrefix = GlobalDictionaryRegistry.normalizeNamespacePrefix(namespacePrefix)
      tables.findNamespaceByPrefix(normalizedPrefix).map { existing =>
        existing.contextClubId.foreach { clubId =>
          policy.requireNamespaceContextMembership(
            newOwner,
            clubId,
            s"transfer ownership for $normalizedPrefix to ${newOwnerId.value}"
          )
        }
        val transferred = existing.transferOwnership(newOwnerId, reviewer, transferredAt, note)
        dictionaryNamespaceRepository.save(transferred)
        auditEventRepository.save(
          AuditEventEntry(
            id = IdGenerator.auditEventId(),
            aggregateType = "dictionary-namespace",
            aggregateId = normalizedPrefix,
            eventType = "DictionaryNamespaceTransferred",
            occurredAt = transferredAt,
            actorId = actor.playerId,
            details = Map(
              "contextClubId" -> existing.contextClubId.map(_.value).getOrElse(""),
              "previousOwnerPlayerId" -> existing.ownerPlayerId.value,
              "ownerPlayerId" -> newOwnerId.value,
              "coOwnerPlayerIds" -> transferred.coOwnerPlayerIds.map(_.value).mkString(","),
              "editorPlayerIds" -> transferred.editorPlayerIds.map(_.value).mkString(",")
            ),
            note = note
          )
        )
        transferred
      }
    }

  def updateDictionaryNamespaceContext(
      namespacePrefix: String,
      contextClubId: Option[ClubId],
      actor: AccessPrincipal,
      note: Option[String] = None,
      updatedAt: Instant = Instant.now()
  ): Option[DictionaryNamespaceRegistration] =
    transactionManager.inTransaction {
      val normalizedPrefix = GlobalDictionaryRegistry.normalizeNamespacePrefix(namespacePrefix)
      tables.findNamespaceByPrefix(normalizedPrefix).map { existing =>
        val reviewer = policy.requireNamespaceManagementActor(actor, existing, s"update context for $normalizedPrefix")
        val owner =
          policy.requireActiveNamespaceOwner(existing.ownerPlayerId, s"update context for $normalizedPrefix owner ${existing.ownerPlayerId.value}")
        val normalizedContextClubId = policy.validateNamespaceContextMembership(
          contextClubId,
          owner,
          existing.coOwnerPlayerIds,
          existing.editorPlayerIds,
          s"update context for $normalizedPrefix"
        )
        val updated = existing.updateContextClub(normalizedContextClubId, reviewer, updatedAt, note)
        dictionaryNamespaceRepository.save(updated)
        auditEventRepository.save(
          AuditEventEntry(
            id = IdGenerator.auditEventId(),
            aggregateType = "dictionary-namespace",
            aggregateId = normalizedPrefix,
            eventType = "DictionaryNamespaceContextUpdated",
            occurredAt = updatedAt,
            actorId = actor.playerId,
            details = Map(
              "previousContextClubId" -> existing.contextClubId.map(_.value).getOrElse(""),
              "contextClubId" -> normalizedContextClubId.map(_.value).getOrElse(""),
              "ownerPlayerId" -> existing.ownerPlayerId.value,
              "coOwnerPlayerIds" -> existing.coOwnerPlayerIds.map(_.value).mkString(","),
              "editorPlayerIds" -> existing.editorPlayerIds.map(_.value).mkString(",")
            ),
            note = note
          )
        )
        updated
      }
    }

  def revokeDictionaryNamespace(
      namespacePrefix: String,
      actor: AccessPrincipal,
      note: Option[String] = None,
      revokedAt: Instant = Instant.now()
  ): Option[DictionaryNamespaceRegistration] =
    transactionManager.inTransaction {
      authorizationService.requirePermission(actor, Permission.ManageGlobalDictionary)
      val reviewer = actor.playerId.getOrElse(PlayerId("system"))
      val normalizedPrefix = GlobalDictionaryRegistry.normalizeNamespacePrefix(namespacePrefix)
      tables.findNamespaceByPrefix(normalizedPrefix).map { existing =>
        val revoked = existing.revoke(reviewer, revokedAt, note)
        dictionaryNamespaceRepository.save(revoked)
        auditEventRepository.save(
          AuditEventEntry(
            id = IdGenerator.auditEventId(),
            aggregateType = "dictionary-namespace",
            aggregateId = normalizedPrefix,
            eventType = "DictionaryNamespaceRevoked",
            occurredAt = revokedAt,
            actorId = actor.playerId,
            details = Map(
              "contextClubId" -> existing.contextClubId.map(_.value).getOrElse(""),
              "ownerPlayerId" -> existing.ownerPlayerId.value,
              "coOwnerPlayerIds" -> existing.coOwnerPlayerIds.map(_.value).mkString(","),
              "editorPlayerIds" -> existing.editorPlayerIds.map(_.value).mkString(",")
            ),
            note = note
          )
        )
        revoked
      }
    }
