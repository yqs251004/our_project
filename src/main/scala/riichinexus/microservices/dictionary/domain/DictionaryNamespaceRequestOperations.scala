package riichinexus.microservices.dictionary.domain

import java.sql.Connection
import java.time.{Duration, Instant}

import riichinexus.application.changes.DomainChangeInterpreter
import riichinexus.bootstrap.DictionaryModuleContext
import riichinexus.domain.model.*
import riichinexus.domain.service.GlobalDictionaryRegistry
import riichinexus.microservices.dictionary.objects.{DictionaryNamespaceRegistration, DictionaryNamespaceReviewStatus}
import riichinexus.microservices.dictionary.tables.dictionarynamespace.DictionaryNamespaceTable

object DictionaryNamespaceRequestOperations:
  def requestNamespace(
      connection: Connection,
      module: DictionaryModuleContext,
      actor: AccessPrincipal,
      namespacePrefix: String,
      ownerPlayerId: Option[PlayerId],
      coOwnerPlayerIds: Vector[PlayerId],
      editorPlayerIds: Vector[PlayerId],
      contextClubId: Option[ClubId],
      reviewDueAt: Option[Instant],
      note: Option[String],
      requestedAt: Instant
  ): DictionaryNamespaceRegistration =
    module.transactionManager.inTransaction {
      val requesterId = actor.playerId.getOrElse(
        throw IllegalArgumentException("Dictionary namespace requests require a registered player identity")
      )
      val effectiveOwner = ownerPlayerId.getOrElse(requesterId)
      if effectiveOwner != requesterId && !actor.isSuperAdmin then
        throw IllegalArgumentException("Only super admins can request a namespace on behalf of another owner")

      val owner = DictionaryNamespaceValidation.requireActiveOwner(
        connection,
        module,
        effectiveOwner,
        s"request ownership for ${effectiveOwner.value}"
      )
      val normalizedPrefix = GlobalDictionaryRegistry.normalizeNamespacePrefix(namespacePrefix)
      val (normalizedCoOwners, normalizedEditors) = DictionaryNamespaceValidation.normalizeCollaborators(
        connection,
        module,
        effectiveOwner,
        coOwnerPlayerIds,
        editorPlayerIds,
        s"request ${namespacePrefix.trim}"
      )
      val effectiveReviewDueAt = reviewDueAt.orElse(Some(requestedAt.plus(Duration.ofHours(72))))
      require(
        effectiveReviewDueAt.forall(!_.isBefore(requestedAt)),
        "Dictionary namespace reviewDueAt cannot be earlier than requestedAt"
      )
      val normalizedContextClubId = DictionaryNamespaceValidation.validateContextMembership(
        connection,
        module,
        contextClubId,
        owner,
        normalizedCoOwners,
        normalizedEditors,
        s"request ${normalizedPrefix.trim}"
      )

      DictionaryNamespaceTable.findByPrefix(connection, normalizedPrefix) match
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
          DomainChangeInterpreter
            .auditOnly(module.transactionManager, module.auditEventRepository)
            .commitAudited(
              aggregate = DictionaryNamespaceRegistration(
                namespacePrefix = normalizedPrefix,
                contextClubId = normalizedContextClubId,
                ownerPlayerId = effectiveOwner,
                coOwnerPlayerIds = normalizedCoOwners,
                editorPlayerIds = normalizedEditors,
                requestedBy = requesterId,
                requestedAt = requestedAt,
                reviewDueAt = effectiveReviewDueAt,
                reviewNote = note
              ),
              persist = module.dictionaryNamespaceRepository.save,
              aggregateType = "dictionary-namespace",
              aggregateId = _.namespacePrefix,
              eventType = "DictionaryNamespaceRequested",
              occurredAt = requestedAt,
              actorId = actor.playerId,
              details = _ =>
                DictionaryNamespaceAudit.details(
                  contextClubId = normalizedContextClubId,
                  ownerPlayerId = effectiveOwner,
                  coOwnerPlayerIds = normalizedCoOwners,
                  editorPlayerIds = normalizedEditors
                ).updated("reviewDueAt", effectiveReviewDueAt.map(_.toString).getOrElse("")),
              note = note
            )
    }
