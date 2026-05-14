package riichinexus.microservices.tournament.appeal.api

import java.net.URI
import java.time.{Duration, Instant}
import java.util.NoSuchElementException

import riichinexus.application.ports.*
import riichinexus.domain.event.*
import riichinexus.domain.model.*
import riichinexus.domain.service.*
import riichinexus.microservices.tournament.api.KnockoutStageCoordinator

private object AppealAttachmentPolicySupport:
  private val MaxAttachmentCount = 12
  private val MaxAttachmentNameLength = 160
  private val MaxAttachmentUriLength = 2048
  private val MaxAttachmentBytes = 150L * 1024L * 1024L
  private val MaxRetentionWindow = Duration.ofDays(365)
  private val AllowedSchemesByStorageKind: Map[AppealAttachmentStorageKind, Set[String]] = Map(
    AppealAttachmentStorageKind.ExternalUrl -> Set("https", "http"),
    AppealAttachmentStorageKind.ObjectStore -> Set("s3", "gs", "riichinexus-object"),
    AppealAttachmentStorageKind.SignedUrl -> Set("https"),
    AppealAttachmentStorageKind.InternalReference -> Set("riichinexus", "app")
  )
  private val AllowedContentTypesByMediaKind: Map[AppealAttachmentMediaKind, Set[String]] = Map(
    AppealAttachmentMediaKind.Image -> Set("image/png", "image/jpeg", "image/webp", "image/gif"),
    AppealAttachmentMediaKind.Video -> Set("video/mp4", "video/webm", "video/quicktime"),
    AppealAttachmentMediaKind.Document -> Set("application/pdf", "text/plain", "text/markdown"),
    AppealAttachmentMediaKind.Log -> Set("text/plain", "application/json", "text/csv"),
    AppealAttachmentMediaKind.Archive -> Set("application/zip", "application/gzip", "application/x-7z-compressed"),
    AppealAttachmentMediaKind.Other -> Set.empty
  )
  private val SupportedChecksumAlgorithms: Map[String, Int] = Map(
    "sha-256" -> 64,
    "sha-512" -> 128
  )

  def validate(
      attachments: Vector[AppealAttachment],
      createdAt: Instant
  ): Vector[AppealAttachment] =
    require(attachments.size <= MaxAttachmentCount, s"Appeals can carry at most $MaxAttachmentCount attachments")
    attachments.zipWithIndex.map { case (attachment, index) =>
      validateAttachment(attachment, createdAt, index + 1)
    }

  private def validateAttachment(
      attachment: AppealAttachment,
      createdAt: Instant,
      position: Int
  ): AppealAttachment =
    val normalizedName = attachment.name.trim
    val normalizedUri = attachment.uri.trim
    val normalizedContentType = attachment.contentType.map(_.trim.toLowerCase).filter(_.nonEmpty)
    val normalizedChecksum = attachment.checksum.map(_.trim.toLowerCase).filter(_.nonEmpty)
    val normalizedAlgorithm = attachment.checksumAlgorithm.map(_.trim.toLowerCase).filter(_.nonEmpty)

    require(normalizedName.nonEmpty, s"Appeal attachment #$position name cannot be empty")
    require(normalizedName.length <= MaxAttachmentNameLength, s"Appeal attachment #$position name is too long")
    require(normalizedUri.nonEmpty, s"Appeal attachment #$position uri cannot be empty")
    require(normalizedUri.length <= MaxAttachmentUriLength, s"Appeal attachment #$position uri is too long")

    val parsedUri =
      try URI(normalizedUri)
      catch
        case _: IllegalArgumentException =>
          throw IllegalArgumentException(s"Appeal attachment #$position uri is not a valid URI")

    val scheme = Option(parsedUri.getScheme).map(_.trim.toLowerCase)
      .getOrElse(throw IllegalArgumentException(s"Appeal attachment #$position uri must include a scheme"))
    require(
      AllowedSchemesByStorageKind.getOrElse(attachment.storageKind, Set.empty).contains(scheme),
      s"Appeal attachment #$position scheme '$scheme' is not allowed for ${attachment.storageKind}"
    )

    attachment.sizeBytes.foreach { sizeBytes =>
      require(sizeBytes <= MaxAttachmentBytes, s"Appeal attachment #$position exceeds $MaxAttachmentBytes bytes")
    }

    normalizedAlgorithm match
      case Some(algorithm) =>
        val expectedLength =
          SupportedChecksumAlgorithms.getOrElse(
            algorithm,
            throw IllegalArgumentException(
              s"Appeal attachment #$position checksum algorithm '$algorithm' is unsupported"
            )
          )
        val checksum =
          normalizedChecksum.getOrElse(
            throw IllegalArgumentException(s"Appeal attachment #$position checksum is required")
          )
        require(checksum.forall(isHexChar), s"Appeal attachment #$position checksum must be hexadecimal")
        require(
          checksum.length == expectedLength,
          s"Appeal attachment #$position checksum length does not match algorithm '$algorithm'"
        )
      case None =>
        require(
          normalizedChecksum.isEmpty,
          s"Appeal attachment #$position checksumAlgorithm is required when checksum is provided"
        )

    normalizedContentType.foreach { contentType =>
      val allowedContentTypes = AllowedContentTypesByMediaKind.getOrElse(attachment.mediaKind, Set.empty)
      require(
        allowedContentTypes.isEmpty || allowedContentTypes.contains(contentType),
        s"Appeal attachment #$position contentType '$contentType' is not allowed for ${attachment.mediaKind}"
      )
    }

    attachment.uploadedAt.foreach { uploadedAt =>
      require(
        !uploadedAt.isAfter(createdAt.plus(Duration.ofHours(1))),
        s"Appeal attachment #$position uploadedAt cannot be unreasonably later than appeal creation"
      )
    }
    attachment.retentionUntil.foreach { retentionUntil =>
      require(
        !retentionUntil.isBefore(createdAt),
        s"Appeal attachment #$position retentionUntil cannot be earlier than appeal creation"
      )
      require(
        !retentionUntil.isAfter(createdAt.plus(MaxRetentionWindow)),
        s"Appeal attachment #$position retentionUntil exceeds the maximum retention window"
      )
    }

    attachment.copy(
      name = normalizedName,
      uri = normalizedUri,
      contentType = normalizedContentType,
      checksum = normalizedChecksum,
      checksumAlgorithm = normalizedAlgorithm
    )

  private def isHexChar(char: Char): Boolean =
    (char >= '0' && char <= '9') ||
      (char >= 'a' && char <= 'f')

final class AppealApplicationService(
    appealTicketRepository: AppealTicketRepository,
    tableRepository: TableRepository,
    playerRepository: PlayerRepository,
    knockoutStageCoordinator: KnockoutStageCoordinator,
    auditEventRepository: AuditEventRepository,
    eventBus: DomainEventBus,
    transactionManager: TransactionManager = NoOpTransactionManager,
    authorizationService: AuthorizationService = NoOpAuthorizationService
):
  def fileAppeal(
      tableId: TableId,
      openedBy: PlayerId,
      description: String,
      attachments: Vector[AppealAttachment] = Vector.empty,
      priority: AppealPriority = AppealPriority.Normal,
      dueAt: Option[Instant] = None,
      actor: AccessPrincipal,
      createdAt: Instant = Instant.now()
  ): Option[AppealTicket] =
    transactionManager.inTransaction {
      tableRepository.findById(tableId).map { table =>
        require(description.trim.nonEmpty, "Appeal description cannot be empty")
        require(dueAt.forall(!_.isBefore(createdAt)), "Appeal dueAt cannot be earlier than createdAt")
        authorizationService.requirePermission(
          actor,
          Permission.FileAppealTicket,
          subjectPlayerId = Some(openedBy)
        )

        if !table.seats.exists(_.playerId == openedBy) then
          throw IllegalArgumentException(s"Player ${openedBy.value} is not seated at table ${tableId.value}")
        if table.status == TableStatus.Archived then
          throw IllegalArgumentException(s"Archived table ${tableId.value} cannot accept new appeals")
        if appealTicketRepository.findAll().exists(ticket =>
            ticket.tableId == tableId &&
              (ticket.status == AppealStatus.Open ||
                ticket.status == AppealStatus.UnderReview ||
                ticket.status == AppealStatus.Escalated)
          )
        then
          throw IllegalArgumentException(
            s"Table ${tableId.value} already has an active appeal ticket"
          )

        val validatedAttachments = AppealAttachmentPolicySupport.validate(attachments, createdAt)
        val ticket = AppealTicket(
          id = IdGenerator.appealTicketId(),
          tableId = table.id,
          tournamentId = table.tournamentId,
          stageId = table.stageId,
          openedBy = openedBy,
          description = description,
          attachments = validatedAttachments,
          priority = priority,
          dueAt = dueAt,
          createdAt = createdAt,
          updatedAt = createdAt
        )

        val savedTicket = appealTicketRepository.save(ticket)
        tableRepository.save(table.flagAppeal(savedTicket.id, Some(description)))
        auditEventRepository.save(
          AuditEventEntry(
            id = IdGenerator.auditEventId(),
            aggregateType = "appeal",
            aggregateId = savedTicket.id.value,
            eventType = "AppealTicketFiled",
            occurredAt = createdAt,
            actorId = Some(openedBy),
            details = Map(
              "tableId" -> tableId.value,
              "attachmentCount" -> savedTicket.attachments.size.toString,
              "attachmentStorageKinds" -> savedTicket.attachments.map(_.storageKind.toString).distinct.sorted.mkString(","),
              "attachmentMediaKinds" -> savedTicket.attachments.map(_.mediaKind.toString).distinct.sorted.mkString(",")
            )
          )
        )
        eventBus.publish(AppealTicketFiled(savedTicket, createdAt))
        savedTicket
      }
    }

  def updateAppealWorkflow(
      ticketId: AppealTicketId,
      actor: AccessPrincipal,
      assigneeId: Option[PlayerId] = None,
      clearAssignee: Boolean = false,
      priority: Option[AppealPriority] = None,
      dueAt: Option[Instant] = None,
      clearDueAt: Boolean = false,
      updatedAt: Instant = Instant.now(),
      note: Option[String] = None
  ): Option[AppealTicket] =
    transactionManager.inTransaction {
      appealTicketRepository.findById(ticketId).map { ticket =>
        authorizationService.requirePermission(
          actor,
          Permission.ResolveAppeal,
          tournamentId = Some(ticket.tournamentId)
        )

        val operatorId = actor.playerId.getOrElse(ticket.openedBy)
        assigneeId.foreach(id => requireActiveAppealOperator(id, "Appeal assignee must be an active player"))
        val nextAssignee =
          if clearAssignee then None
          else assigneeId.orElse(ticket.assigneeId)
        val nextPriority = priority.getOrElse(ticket.priority)
        val nextDueAt =
          if clearDueAt then None
          else dueAt.orElse(ticket.dueAt)

        require(nextDueAt.forall(!_.isBefore(updatedAt)), "Appeal dueAt cannot be earlier than workflow update time")

        val reassignedTicket =
          if nextAssignee != ticket.assigneeId then
            ticket.assign(operatorId, nextAssignee, updatedAt, note)
          else ticket

        val triagedTicket =
          if nextPriority != reassignedTicket.priority || nextDueAt != reassignedTicket.dueAt then
            reassignedTicket.reprioritize(operatorId, nextPriority, nextDueAt, updatedAt, note)
          else reassignedTicket

        val savedTicket = appealTicketRepository.save(triagedTicket.copy(updatedAt = updatedAt))
        eventBus.publish(AppealTicketWorkflowUpdated(savedTicket, updatedAt))
        auditEventRepository.save(
          AuditEventEntry(
            id = IdGenerator.auditEventId(),
            aggregateType = "appeal",
            aggregateId = ticketId.value,
            eventType = "AppealTicketWorkflowUpdated",
            occurredAt = updatedAt,
            actorId = actor.playerId,
            details = Map(
              "tournamentId" -> ticket.tournamentId.value,
              "tableId" -> ticket.tableId.value,
              "assigneeId" -> savedTicket.assigneeId.map(_.value).getOrElse("none"),
              "priority" -> savedTicket.priority.toString,
              "dueAt" -> savedTicket.dueAt.map(_.toString).getOrElse("none")
            ),
            note = note
          )
        )
        savedTicket
      }
    }

  def resolveAppeal(
      ticketId: AppealTicketId,
      verdict: String,
      actor: AccessPrincipal,
      resolvedAt: Instant = Instant.now(),
      note: Option[String] = None
  ): Option[AppealTicket] =
    adjudicateAppeal(
      ticketId = ticketId,
      decision = AppealDecisionType.Resolve,
      verdict = verdict,
      actor = actor,
      adjudicatedAt = resolvedAt,
      tableResolution = Some(AppealTableResolution.RestorePriorState),
      note = note
    )

  def adjudicateAppeal(
      ticketId: AppealTicketId,
      decision: AppealDecisionType,
      verdict: String,
      actor: AccessPrincipal,
      adjudicatedAt: Instant = Instant.now(),
      tableResolution: Option[AppealTableResolution] = None,
      note: Option[String] = None
  ): Option[AppealTicket] =
    transactionManager.inTransaction {
      appealTicketRepository.findById(ticketId).map { ticket =>
        authorizationService.requirePermission(
          actor,
          Permission.ResolveAppeal,
          tournamentId = Some(ticket.tournamentId)
        )

        val operatorId = actor.playerId.getOrElse(ticket.openedBy)
        val reviewedTicket =
          if ticket.status == AppealStatus.UnderReview then ticket
          else ticket.markUnderReview(operatorId, adjudicatedAt, note)

        val adjudicatedTicket =
          decision match
            case AppealDecisionType.Resolve =>
              reviewedTicket.resolve(operatorId, verdict, adjudicatedAt, note)
            case AppealDecisionType.Reject =>
              reviewedTicket.reject(operatorId, verdict, adjudicatedAt, note)
            case AppealDecisionType.Escalate =>
              reviewedTicket.escalate(operatorId, verdict, adjudicatedAt, note)

        appealTicketRepository.save(adjudicatedTicket)

        if decision != AppealDecisionType.Escalate then
          tableRepository.findById(ticket.tableId).foreach { table =>
            val updatedTable =
              tableResolution.getOrElse(AppealTableResolution.RestorePriorState) match
                case AppealTableResolution.ForceReset =>
                  table.forceReset(
                    note.getOrElse(s"Appeal ${ticketId.value} adjudication requested reset"),
                    adjudicatedAt
                  )
                case resolution =>
                  table.resolveAppeal(resolution, note)

            tableRepository.save(updatedTable)

            if updatedTable.bracketMatchId.nonEmpty && updatedTable.status != TableStatus.Archived then
              knockoutStageCoordinator.reconcileAfterMatchMutation(
                updatedTable.tournamentId,
                updatedTable.stageId,
                updatedTable.bracketMatchId.get,
                adjudicatedAt
              )
          }

        decision match
          case AppealDecisionType.Resolve =>
            eventBus.publish(AppealTicketResolved(adjudicatedTicket, adjudicatedAt))
          case _ =>
            ()

        eventBus.publish(
          AppealTicketAdjudicated(
            ticket = adjudicatedTicket,
            decision = decision,
            tableResolution =
              if decision == AppealDecisionType.Escalate then None
              else tableResolution.orElse(Some(AppealTableResolution.RestorePriorState)),
            occurredAt = adjudicatedAt
          )
        )
        auditEventRepository.save(
          AuditEventEntry(
            id = IdGenerator.auditEventId(),
            aggregateType = "appeal",
            aggregateId = ticketId.value,
            eventType = "AppealTicketAdjudicated",
            occurredAt = adjudicatedAt,
            actorId = actor.playerId,
            details = Map(
              "decision" -> decision.toString,
              "tournamentId" -> ticket.tournamentId.value,
              "tableId" -> ticket.tableId.value,
              "tableResolution" -> tableResolution.map(_.toString).getOrElse("none")
            ),
            note = note.orElse(Some(verdict))
          )
        )
        adjudicatedTicket
      }
    }

  def reopenAppeal(
      ticketId: AppealTicketId,
      reason: String,
      actor: AccessPrincipal,
      reopenedAt: Instant = Instant.now(),
      note: Option[String] = None
  ): Option[AppealTicket] =
    transactionManager.inTransaction {
      appealTicketRepository.findById(ticketId).map { ticket =>
        val operatorId = actor.playerId.getOrElse(ticket.openedBy)
        if actor.playerId.contains(ticket.openedBy) then ()
        else
          authorizationService.requirePermission(
            actor,
            Permission.ResolveAppeal,
            tournamentId = Some(ticket.tournamentId)
          )

        val reopenedTicket = appealTicketRepository.save(ticket.reopen(operatorId, reason, reopenedAt, note))
        tableRepository.findById(ticket.tableId).foreach { table =>
          if table.status != TableStatus.Archived then
            tableRepository.save(table.flagAppeal(ticket.id, note.orElse(Some(s"Appeal ${ticket.id.value} reopened"))))
        }
        eventBus.publish(AppealTicketReopened(reopenedTicket, reopenedAt))
        auditEventRepository.save(
          AuditEventEntry(
            id = IdGenerator.auditEventId(),
            aggregateType = "appeal",
            aggregateId = ticketId.value,
            eventType = "AppealTicketReopened",
            occurredAt = reopenedAt,
            actorId = actor.playerId,
            details = Map(
              "tournamentId" -> ticket.tournamentId.value,
              "tableId" -> ticket.tableId.value,
              "reopenCount" -> reopenedTicket.reopenCount.toString
            ),
            note = note.orElse(Some(reason))
          )
        )
        reopenedTicket
      }
    }

  private def requireActiveAppealOperator(playerId: PlayerId, context: String): Unit =
    val player = playerRepository.findById(playerId)
      .getOrElse(throw NoSuchElementException(s"Player ${playerId.value} was not found"))
    if player.status != PlayerStatus.Active then
      throw IllegalArgumentException(context)
