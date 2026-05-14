package riichinexus.microservices.dictionary.api

import java.time.Instant

import riichinexus.application.ports.*
import riichinexus.domain.model.*
import riichinexus.domain.service.AuthorizationService
import riichinexus.microservices.dictionary.api.responses.DictionaryNamespaceBacklogView
import riichinexus.microservices.dictionary.tables.DictionaryTables

final class DictionaryGovernanceService(
    playerRepository: PlayerRepository,
    clubRepository: ClubRepository,
    globalDictionaryRepository: GlobalDictionaryRepository,
    dictionaryNamespaceRepository: DictionaryNamespaceRepository,
    auditEventRepository: AuditEventRepository,
    eventBus: DomainEventBus,
    transactionManager: TransactionManager,
    authorizationService: AuthorizationService
):
  private val tables = DictionaryTables(
    globalDictionaryRepository = globalDictionaryRepository,
    dictionaryNamespaceRepository = dictionaryNamespaceRepository
  )

  private val namespacePolicy = DictionaryNamespacePolicySupport(
    playerRepository = playerRepository,
    clubRepository = clubRepository
  )

  private val namespaceGovernance = DictionaryNamespaceGovernanceService(
    tables = tables,
    dictionaryNamespaceRepository = dictionaryNamespaceRepository,
    auditEventRepository = auditEventRepository,
    transactionManager = transactionManager,
    authorizationService = authorizationService,
    policy = namespacePolicy
  )

  private val namespaceReminders = DictionaryNamespaceReminderService(
    tables = tables,
    dictionaryNamespaceRepository = dictionaryNamespaceRepository,
    auditEventRepository = auditEventRepository,
    transactionManager = transactionManager,
    authorizationService = authorizationService
  )

  private val entries = DictionaryEntryGovernanceService(
    tables = tables,
    globalDictionaryRepository = globalDictionaryRepository,
    auditEventRepository = auditEventRepository,
    eventBus = eventBus,
    transactionManager = transactionManager,
    authorizationService = authorizationService,
    policy = namespacePolicy
  )

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
    namespaceGovernance.requestDictionaryNamespace(
      namespacePrefix = namespacePrefix,
      actor = actor,
      contextClubId = contextClubId,
      ownerPlayerId = ownerPlayerId,
      coOwnerPlayerIds = coOwnerPlayerIds,
      editorPlayerIds = editorPlayerIds,
      note = note,
      requestedAt = requestedAt,
      reviewDueAt = reviewDueAt
    )

  def reviewDictionaryNamespace(
      namespacePrefix: String,
      approve: Boolean,
      actor: AccessPrincipal,
      note: Option[String] = None,
      reviewedAt: Instant = Instant.now()
  ): Option[DictionaryNamespaceRegistration] =
    namespaceGovernance.reviewDictionaryNamespace(
      namespacePrefix = namespacePrefix,
      approve = approve,
      actor = actor,
      note = note,
      reviewedAt = reviewedAt
    )

  def updateDictionaryNamespaceCollaborators(
      namespacePrefix: String,
      coOwnerPlayerIds: Vector[PlayerId],
      editorPlayerIds: Vector[PlayerId],
      actor: AccessPrincipal,
      note: Option[String] = None,
      updatedAt: Instant = Instant.now()
  ): Option[DictionaryNamespaceRegistration] =
    namespaceGovernance.updateDictionaryNamespaceCollaborators(
      namespacePrefix = namespacePrefix,
      coOwnerPlayerIds = coOwnerPlayerIds,
      editorPlayerIds = editorPlayerIds,
      actor = actor,
      note = note,
      updatedAt = updatedAt
    )

  def dictionaryNamespaceBacklog(
      actor: AccessPrincipal,
      asOf: Instant = Instant.now(),
      dueSoonWindow: java.time.Duration = java.time.Duration.ofHours(24)
  ): DictionaryNamespaceBacklogView =
    namespaceReminders.dictionaryNamespaceBacklog(
      actor = actor,
      asOf = asOf,
      dueSoonWindow = dueSoonWindow
    )

  def processDictionaryNamespaceReminders(
      actor: AccessPrincipal,
      asOf: Instant = Instant.now(),
      dueSoonWindow: java.time.Duration = java.time.Duration.ofHours(24),
      reminderInterval: java.time.Duration = java.time.Duration.ofHours(12),
      escalationGrace: java.time.Duration = java.time.Duration.ofHours(72)
  ): Vector[DictionaryNamespaceReminderAction] =
    namespaceReminders.processDictionaryNamespaceReminders(
      actor = actor,
      asOf = asOf,
      dueSoonWindow = dueSoonWindow,
      reminderInterval = reminderInterval,
      escalationGrace = escalationGrace
    )

  def transferDictionaryNamespace(
      namespacePrefix: String,
      newOwnerId: PlayerId,
      actor: AccessPrincipal,
      note: Option[String] = None,
      transferredAt: Instant = Instant.now()
  ): Option[DictionaryNamespaceRegistration] =
    namespaceGovernance.transferDictionaryNamespace(
      namespacePrefix = namespacePrefix,
      newOwnerId = newOwnerId,
      actor = actor,
      note = note,
      transferredAt = transferredAt
    )

  def updateDictionaryNamespaceContext(
      namespacePrefix: String,
      contextClubId: Option[ClubId],
      actor: AccessPrincipal,
      note: Option[String] = None,
      updatedAt: Instant = Instant.now()
  ): Option[DictionaryNamespaceRegistration] =
    namespaceGovernance.updateDictionaryNamespaceContext(
      namespacePrefix = namespacePrefix,
      contextClubId = contextClubId,
      actor = actor,
      note = note,
      updatedAt = updatedAt
    )

  def revokeDictionaryNamespace(
      namespacePrefix: String,
      actor: AccessPrincipal,
      note: Option[String] = None,
      revokedAt: Instant = Instant.now()
  ): Option[DictionaryNamespaceRegistration] =
    namespaceGovernance.revokeDictionaryNamespace(
      namespacePrefix = namespacePrefix,
      actor = actor,
      note = note,
      revokedAt = revokedAt
    )

  def upsertDictionary(
      key: String,
      value: String,
      actor: AccessPrincipal,
      note: Option[String] = None,
      updatedAt: Instant = Instant.now()
  ): GlobalDictionaryEntry =
    entries.upsertDictionary(
      key = key,
      value = value,
      actor = actor,
      note = note,
      updatedAt = updatedAt
    )
