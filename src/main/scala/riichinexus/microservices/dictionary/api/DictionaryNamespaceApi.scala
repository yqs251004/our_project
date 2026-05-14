package riichinexus.microservices.dictionary.api

import java.time.Instant

import riichinexus.domain.model.*
import riichinexus.microservices.dictionary.api.requests.*
import riichinexus.microservices.dictionary.api.responses.DictionaryNamespaceBacklogView
import riichinexus.microservices.dictionary.objects.{DictionaryNamespaceBacklogQuery, DictionaryNamespaceListQuery}
import riichinexus.microservices.dictionary.tables.DictionaryTables

object DictionaryNamespaceApi:

  def backlog(
      governance: DictionaryGovernanceService,
      actor: AccessPrincipal,
      query: DictionaryNamespaceBacklogQuery
  ): DictionaryNamespaceBacklogView =
    governance.dictionaryNamespaceBacklog(
      actor = actor,
      asOf = query.asOf,
      dueSoonWindow = java.time.Duration.ofHours(query.dueSoonHours)
    )

  def listNamespaces(
      tables: DictionaryTables,
      actor: AccessPrincipal,
      query: DictionaryNamespaceListQuery
  ): Vector[DictionaryNamespaceRegistration] =
    tables.listNamespaces()
      .filter(registration => query.status.forall(_ == registration.status))
      .filter(registration => query.contextClubId.forall(clubId => registration.contextClubId.contains(clubId)))
      .filter(registration => query.ownerId.forall(_ == registration.ownerPlayerId))
      .filter(registration => query.requestedBy.forall(_ == registration.requestedBy))
      .filter(registration => query.reviewedBy.forall(reviewer => registration.reviewedBy.contains(reviewer)))
      .filter(registration => !query.overdueOnly || registration.isPendingOverdue(query.asOf))
      .filter(registration => query.dueBefore.forall(bound => registration.reviewDueAt.exists(dueAt => !dueAt.isAfter(bound))))
      .filter(registration => query.dueAfter.forall(bound => registration.reviewDueAt.exists(dueAt => !dueAt.isBefore(bound))))
      .filter(registration =>
        actor.isSuperAdmin ||
          actor.playerId.exists(registration.hasWriteAccess) ||
          actor.playerId.contains(registration.requestedBy)
      )

  def requestNamespace(
      governance: DictionaryGovernanceService,
      actor: AccessPrincipal,
      request: RequestDictionaryNamespaceRequest
  ): DictionaryNamespaceRegistration =
    governance.requestDictionaryNamespace(
      namespacePrefix = request.namespacePrefix,
      actor = actor,
      contextClubId = request.contextClub,
      ownerPlayerId = request.owner,
      coOwnerPlayerIds = request.coOwners,
      editorPlayerIds = request.editors,
      note = request.note,
      reviewDueAt = request.parsedReviewDueAt
    )

  def reviewNamespace(
      governance: DictionaryGovernanceService,
      actor: AccessPrincipal,
      request: ReviewDictionaryNamespaceRequest
  ): Option[DictionaryNamespaceRegistration] =
    governance.reviewDictionaryNamespace(
      namespacePrefix = request.namespacePrefix,
      approve = request.approve,
      actor = actor,
      note = request.note
    )

  def transferNamespace(
      governance: DictionaryGovernanceService,
      actor: AccessPrincipal,
      request: TransferDictionaryNamespaceRequest
  ): Option[DictionaryNamespaceRegistration] =
    governance.transferDictionaryNamespace(
      namespacePrefix = request.namespacePrefix,
      newOwnerId = request.newOwner,
      actor = actor,
      note = request.note
    )

  def updateCollaborators(
      governance: DictionaryGovernanceService,
      actor: AccessPrincipal,
      request: UpdateDictionaryNamespaceCollaboratorsRequest
  ): Option[DictionaryNamespaceRegistration] =
    governance.updateDictionaryNamespaceCollaborators(
      namespacePrefix = request.namespacePrefix,
      coOwnerPlayerIds = request.coOwners,
      editorPlayerIds = request.editors,
      actor = actor,
      note = request.note
    )

  def updateContext(
      governance: DictionaryGovernanceService,
      actor: AccessPrincipal,
      request: UpdateDictionaryNamespaceContextRequest
  ): Option[DictionaryNamespaceRegistration] =
    governance.updateDictionaryNamespaceContext(
      namespacePrefix = request.namespacePrefix,
      contextClubId = request.contextClub,
      actor = actor,
      note = request.note
    )

  def processReminders(
      governance: DictionaryGovernanceService,
      actor: AccessPrincipal,
      request: ProcessDictionaryNamespaceRemindersRequest,
      fallbackAsOf: Instant = Instant.now()
  ): Vector[DictionaryNamespaceReminderAction] =
    governance.processDictionaryNamespaceReminders(
      actor = actor,
      asOf = request.parsedAsOf.getOrElse(fallbackAsOf),
      dueSoonWindow = java.time.Duration.ofHours(request.dueSoonHours.toLong),
      reminderInterval = java.time.Duration.ofHours(request.reminderIntervalHours.toLong),
      escalationGrace = java.time.Duration.ofHours(request.escalationGraceHours.toLong)
    )

  def revokeNamespace(
      governance: DictionaryGovernanceService,
      actor: AccessPrincipal,
      request: RevokeDictionaryNamespaceRequest
  ): Option[DictionaryNamespaceRegistration] =
    governance.revokeDictionaryNamespace(
      namespacePrefix = request.namespacePrefix,
      actor = actor,
      note = request.note
    )
