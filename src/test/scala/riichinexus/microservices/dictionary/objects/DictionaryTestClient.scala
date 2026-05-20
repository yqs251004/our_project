package riichinexus.microservices.dictionary.objects

import java.time.{Duration, Instant}

import riichinexus.bootstrap.DictionaryModuleContext
import riichinexus.domain.event.GlobalDictionaryUpdated
import riichinexus.domain.model.*
import riichinexus.domain.service.GlobalDictionaryRegistry
import riichinexus.microservices.dictionary.objects.apiTypes.*

final class DictionaryTestClient(module: DictionaryModuleContext):
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
    module.transactionManager.inTransaction {
      val requesterId = actor.playerId.getOrElse(
        throw IllegalArgumentException("Dictionary namespace requests require a registered player identity")
      )
      val effectiveOwner = ownerPlayerId.getOrElse(requesterId)
      if effectiveOwner != requesterId && !actor.isSuperAdmin then
        throw IllegalArgumentException("Only super admins can request a namespace on behalf of another owner")
      val owner = requireActiveNamespaceOwner(effectiveOwner, s"request ownership for ${effectiveOwner.value}")
      val (normalizedCoOwners, normalizedEditors) = normalizeNamespaceCollaborators(
        effectiveOwner,
        coOwnerPlayerIds,
        editorPlayerIds,
        s"request ${namespacePrefix.trim}"
      )

      val normalizedPrefix = GlobalDictionaryRegistry.normalizeNamespacePrefix(namespacePrefix)
      val effectiveReviewDueAt = reviewDueAt.orElse(Some(requestedAt.plus(Duration.ofHours(72))))
      require(
        effectiveReviewDueAt.forall(!_.isBefore(requestedAt)),
        "Dictionary namespace reviewDueAt cannot be earlier than requestedAt"
      )
      val normalizedContextClubId = validateNamespaceContextMembership(
        contextClubId,
        owner,
        normalizedCoOwners,
        normalizedEditors,
        s"request ${normalizedPrefix.trim}"
      )
      module.tables.findNamespaceByPrefix(normalizedPrefix) match
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
          val registration = module.dictionaryNamespaceRepository.save(
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
          module.auditEventRepository.save(
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
    module.transactionManager.inTransaction {
      module.authorizationService.requirePermission(actor, Permission.ManageGlobalDictionary)
      val reviewer = actor.playerId.getOrElse(PlayerId("system"))
      val normalizedPrefix = GlobalDictionaryRegistry.normalizeNamespacePrefix(namespacePrefix)
      module.tables.findNamespaceByPrefix(normalizedPrefix).map { existing =>
        val reviewed =
          if approve then existing.approve(reviewer, reviewedAt, note)
          else existing.reject(reviewer, reviewedAt, note)

        module.dictionaryNamespaceRepository.save(reviewed)
        module.auditEventRepository.save(
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
    module.transactionManager.inTransaction {
      val normalizedPrefix = GlobalDictionaryRegistry.normalizeNamespacePrefix(namespacePrefix)
      module.tables.findNamespaceByPrefix(normalizedPrefix).map { existing =>
        val reviewer = requireNamespaceManagementActor(actor, existing, s"update collaborators for $normalizedPrefix")
        val (normalizedCoOwners, normalizedEditors) = normalizeNamespaceCollaborators(
          existing.ownerPlayerId,
          coOwnerPlayerIds,
          editorPlayerIds,
          s"update collaborators for $normalizedPrefix"
        )
        validateNamespaceContextMembership(
          existing.contextClubId,
          requireActiveNamespaceOwner(existing.ownerPlayerId, s"update collaborators for $normalizedPrefix owner ${existing.ownerPlayerId.value}"),
          normalizedCoOwners,
          normalizedEditors,
          s"update collaborators for $normalizedPrefix"
        )
        val updated = existing.updateCollaborators(normalizedCoOwners, normalizedEditors, reviewer, updatedAt, note)
        module.dictionaryNamespaceRepository.save(updated)
        module.auditEventRepository.save(
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
    module.transactionManager.inTransaction {
      module.authorizationService.requirePermission(actor, Permission.ManageGlobalDictionary)
      val reviewer = actor.playerId.getOrElse(PlayerId("system"))
      val newOwner = requireActiveNamespaceOwner(newOwnerId, s"transfer ownership to ${newOwnerId.value}")
      val normalizedPrefix = GlobalDictionaryRegistry.normalizeNamespacePrefix(namespacePrefix)
      module.tables.findNamespaceByPrefix(normalizedPrefix).map { existing =>
        existing.contextClubId.foreach { clubId =>
          requireNamespaceContextMembership(
            newOwner,
            clubId,
            s"transfer ownership for $normalizedPrefix to ${newOwnerId.value}"
          )
        }
        val transferred = existing.transferOwnership(newOwnerId, reviewer, transferredAt, note)
        module.dictionaryNamespaceRepository.save(transferred)
        module.auditEventRepository.save(
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
    module.transactionManager.inTransaction {
      val normalizedPrefix = GlobalDictionaryRegistry.normalizeNamespacePrefix(namespacePrefix)
      module.tables.findNamespaceByPrefix(normalizedPrefix).map { existing =>
        val reviewer = requireNamespaceManagementActor(actor, existing, s"update context for $normalizedPrefix")
        val owner = requireActiveNamespaceOwner(existing.ownerPlayerId, s"update context for $normalizedPrefix owner ${existing.ownerPlayerId.value}")
        val normalizedContextClubId = validateNamespaceContextMembership(
          contextClubId,
          owner,
          existing.coOwnerPlayerIds,
          existing.editorPlayerIds,
          s"update context for $normalizedPrefix"
        )
        val updated = existing.updateContextClub(normalizedContextClubId, reviewer, updatedAt, note)
        module.dictionaryNamespaceRepository.save(updated)
        module.auditEventRepository.save(
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
    module.transactionManager.inTransaction {
      module.authorizationService.requirePermission(actor, Permission.ManageGlobalDictionary)
      val reviewer = actor.playerId.getOrElse(PlayerId("system"))
      val normalizedPrefix = GlobalDictionaryRegistry.normalizeNamespacePrefix(namespacePrefix)
      module.tables.findNamespaceByPrefix(normalizedPrefix).map { existing =>
        val revoked = existing.revoke(reviewer, revokedAt, note)
        module.dictionaryNamespaceRepository.save(revoked)
        module.auditEventRepository.save(
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

  def dictionaryNamespaceBacklog(
      actor: AccessPrincipal,
      asOf: Instant = Instant.now(),
      dueSoonWindow: Duration = Duration.ofHours(24)
  ): DictionaryNamespaceBacklogView =
    module.transactionManager.inTransaction {
      module.authorizationService.requirePermission(actor, Permission.ManageGlobalDictionary)
      val pending = module.tables.listNamespaces()
        .filter(_.status == DictionaryNamespaceReviewStatus.Pending)

      val ownerBacklog = pending
        .groupBy(_.ownerPlayerId)
        .toVector
        .map { case (ownerId, registrations) =>
          DictionaryNamespaceOwnerBacklog(
            ownerPlayerId = ownerId,
            pendingCount = registrations.size,
            overdueCount = registrations.count(_.isPendingOverdue(asOf)),
            dueSoonCount = registrations.count(_.isPendingDueSoon(asOf, dueSoonWindow))
          )
        }
        .sortBy(bucket => (-bucket.overdueCount, -bucket.pendingCount, bucket.ownerPlayerId.value))

      DictionaryNamespaceBacklogView(
        asOf = asOf,
        pendingCount = pending.size,
        overdueCount = pending.count(_.isPendingOverdue(asOf)),
        dueSoonCount = pending.count(_.isPendingDueSoon(asOf, dueSoonWindow)),
        oldestPendingRequestedAt = pending.map(_.requestedAt).sorted.headOption,
        nextDueAt = pending.flatMap(_.reviewDueAt).sorted.headOption,
        ownerBacklog = ownerBacklog
      )
    }

  def processDictionaryNamespaceReminders(
      actor: AccessPrincipal,
      asOf: Instant = Instant.now(),
      dueSoonWindow: Duration = Duration.ofHours(24),
      reminderInterval: Duration = Duration.ofHours(12),
      escalationGrace: Duration = Duration.ofHours(72)
  ): Vector[DictionaryNamespaceReminderAction] =
    module.transactionManager.inTransaction {
      module.authorizationService.requirePermission(actor, Permission.ManageGlobalDictionary)
      module.tables.listNamespaces()
        .filter(_.status == DictionaryNamespaceReviewStatus.Pending)
        .flatMap { registration =>
          reminderKindFor(registration, asOf, escalationGrace)
            .filter { kind =>
              kind != DictionaryNamespaceReminderKind.DueSoon || registration.isPendingDueSoon(asOf, dueSoonWindow)
            }
            .filter(_ =>
              registration.lastReminderAt.forall(lastSentAt =>
                lastSentAt.plus(reminderInterval).isBefore(asOf) || lastSentAt.plus(reminderInterval).equals(asOf)
              )
            )
            .map { reminderKind =>
              val updated = registration.markReminderSent(asOf)
              module.dictionaryNamespaceRepository.save(updated)
              module.auditEventRepository.save(
                AuditEventEntry(
                  id = IdGenerator.auditEventId(),
                  aggregateType = "dictionary-namespace",
                  aggregateId = registration.namespacePrefix,
                  eventType = "DictionaryNamespaceReminderTriggered",
                  occurredAt = asOf,
                  actorId = actor.playerId,
                  details = Map(
                    "contextClubId" -> registration.contextClubId.map(_.value).getOrElse(""),
                    "ownerPlayerId" -> registration.ownerPlayerId.value,
                    "coOwnerPlayerIds" -> registration.coOwnerPlayerIds.map(_.value).mkString(","),
                    "editorPlayerIds" -> registration.editorPlayerIds.map(_.value).mkString(","),
                    "reminderKind" -> reminderKind.toString,
                    "reminderCount" -> updated.reminderCount.toString,
                    "reviewDueAt" -> registration.reviewDueAt.map(_.toString).getOrElse("")
                  ),
                  note = Some(s"Namespace ${registration.namespacePrefix} is ${reminderKind.toString.toLowerCase}")
                )
              )
              DictionaryNamespaceReminderAction(
                namespacePrefix = registration.namespacePrefix,
                contextClubId = registration.contextClubId,
                ownerPlayerId = registration.ownerPlayerId,
                coOwnerPlayerIds = registration.coOwnerPlayerIds,
                editorPlayerIds = registration.editorPlayerIds,
                reminderKind = reminderKind,
                triggeredAt = asOf,
                dueAt = registration.reviewDueAt,
                reminderCount = updated.reminderCount
              )
            }
        }
        .sortBy(action => (action.namespacePrefix, action.reminderKind.toString))
    }

  def upsertDictionary(
      key: String,
      value: String,
      actor: AccessPrincipal,
      note: Option[String] = None,
      updatedAt: Instant = Instant.now()
  ): GlobalDictionaryEntry =
    module.transactionManager.inTransaction {
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
          requireNamespaceWriterActor(actorId, namespace, s"write ${key.trim}")
      else
        module.authorizationService.requirePermission(actor, Permission.ManageGlobalDictionary)

      val existingVersion = module.tables.findEntryByKey(key).map(_.version).getOrElse(0)
      val entry = GlobalDictionaryEntry(
        key = key,
        value = value,
        updatedAt = updatedAt,
        updatedBy = actor.playerId.getOrElse(PlayerId("system")),
        note = note,
        version = existingVersion
      )

      val saved = module.globalDictionaryRepository.save(entry)
      module.auditEventRepository.save(
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
      module.eventBus.publish(GlobalDictionaryUpdated(saved, updatedAt))
      saved
    }

  private def reminderKindFor(
      registration: DictionaryNamespaceRegistration,
      asOf: Instant,
      escalationGrace: Duration
  ): Option[DictionaryNamespaceReminderKind] =
    registration.reviewDueAt.flatMap { dueAt =>
      if registration.status != DictionaryNamespaceReviewStatus.Pending then None
      else if !dueAt.isBefore(asOf) then Some(DictionaryNamespaceReminderKind.DueSoon)
      else if !dueAt.plus(escalationGrace).isAfter(asOf) then Some(DictionaryNamespaceReminderKind.Escalated)
      else Some(DictionaryNamespaceReminderKind.Overdue)
    }

  private def approvedMetadataNamespaceForKey(key: String): Option[DictionaryNamespaceRegistration] =
    val normalizedKey = GlobalDictionaryRegistry.normalizeKey(key)
    module.tables.listApprovedNamespaces()
      .filter(registration => normalizedKey.startsWith(registration.namespacePrefix))
      .sortBy(_.namespacePrefix.length)
      .lastOption

  private def requireNamespaceWriterActor(
      actorId: PlayerId,
      registration: DictionaryNamespaceRegistration,
      action: String
  ): Unit =
    if !registration.hasWriteAccess(actorId) then
      throw IllegalArgumentException(
        s"Metadata namespace ${registration.namespacePrefix} is writable only by its owners/editors"
      )
    registration.contextClubId.foreach { clubId =>
      val player = requireActiveNamespaceOwner(actorId, s"$action writer ${actorId.value}")
      requireNamespaceContextMembership(player, clubId, s"$action writer ${actorId.value}")
    }

  private def requireNamespaceManagementActor(
      actor: AccessPrincipal,
      registration: DictionaryNamespaceRegistration,
      action: String
  ): PlayerId =
    if actor.isSuperAdmin then actor.playerId.getOrElse(PlayerId("system"))
    else
      val actorId = actor.playerId.getOrElse(
        throw IllegalArgumentException(s"Dictionary namespace $action requires a registered player identity")
      )
      if registration.hasOwnership(actorId) then actorId
      else
        throw IllegalArgumentException(
          s"Dictionary namespace ${registration.namespacePrefix} can only be managed by a super admin or one of its owners"
        )

  private def requireActiveNamespaceOwner(playerId: PlayerId, action: String): Player =
    module.playerRepository.findById(playerId) match
      case Some(player) if player.status == PlayerStatus.Active => player
      case Some(player) =>
        throw IllegalArgumentException(
          s"Dictionary namespace $action requires an active player owner, but ${playerId.value} is ${player.status.toString.toLowerCase}"
        )
      case None =>
        throw IllegalArgumentException(
          s"Dictionary namespace $action requires an existing player owner, but ${playerId.value} was not found"
        )

  private def validateNamespaceContextMembership(
      contextClubId: Option[ClubId],
      owner: Player,
      coOwnerPlayerIds: Vector[PlayerId],
      editorPlayerIds: Vector[PlayerId],
      action: String
  ): Option[ClubId] =
    contextClubId.map { clubId =>
      module.clubRepository.findById(clubId).getOrElse(
        throw IllegalArgumentException(
          s"Dictionary namespace $action requires an existing context club, but ${clubId.value} was not found"
        )
      )
      requireNamespaceContextMembership(owner, clubId, s"$action owner ${owner.id.value}")
      coOwnerPlayerIds.foreach { playerId =>
        val player = requireActiveNamespaceOwner(playerId, s"$action co-owner ${playerId.value}")
        requireNamespaceContextMembership(player, clubId, s"$action co-owner ${playerId.value}")
      }
      editorPlayerIds.foreach { playerId =>
        val player = requireActiveNamespaceOwner(playerId, s"$action editor ${playerId.value}")
        requireNamespaceContextMembership(player, clubId, s"$action editor ${playerId.value}")
      }
      clubId
    }

  private def requireNamespaceContextMembership(player: Player, contextClubId: ClubId, action: String): Unit =
    if !player.boundClubIds.contains(contextClubId) then
      throw IllegalArgumentException(
        s"Dictionary namespace $action requires ${player.id.value} to belong to context club ${contextClubId.value}"
      )

  private def normalizeNamespaceCollaborators(
      ownerPlayerId: PlayerId,
      coOwnerPlayerIds: Vector[PlayerId],
      editorPlayerIds: Vector[PlayerId],
      action: String
  ): (Vector[PlayerId], Vector[PlayerId]) =
    val normalizedCoOwners = coOwnerPlayerIds.distinct.filterNot(_ == ownerPlayerId)
    normalizedCoOwners.foreach(playerId => requireActiveNamespaceOwner(playerId, s"$action co-owner ${playerId.value}"))
    val normalizedEditors =
      editorPlayerIds.distinct.filterNot(playerId => playerId == ownerPlayerId || normalizedCoOwners.contains(playerId))
    normalizedEditors.foreach(playerId => requireActiveNamespaceOwner(playerId, s"$action editor ${playerId.value}"))
    (normalizedCoOwners, normalizedEditors)
