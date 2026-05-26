package riichinexus.microservices.dictionary.domain

import java.time.Instant

import riichinexus.application.changes.DomainChangeInterpreter
import riichinexus.bootstrap.DictionaryModuleContext
import riichinexus.domain.model.*
import riichinexus.microservices.dictionary.objects.DictionaryNamespaceRegistration

private[dictionary] object DictionaryNamespaceAudit:
  def commit(
      module: DictionaryModuleContext,
      aggregate: DictionaryNamespaceRegistration,
      eventType: String,
      occurredAt: Instant,
      actorId: Option[PlayerId],
      details: Map[String, String],
      note: Option[String]
  ): DictionaryNamespaceRegistration =
    DomainChangeInterpreter
      .auditOnly(module.transactionManager, module.auditEventRepository)
      .commitAudited(
        aggregate = aggregate,
        persist = module.dictionaryNamespaceRepository.save,
        aggregateType = "dictionary-namespace",
        aggregateId = _.namespacePrefix,
        eventType = eventType,
        occurredAt = occurredAt,
        actorId = actorId,
        details = savedRegistration =>
          details.updated("reminderCount", savedRegistration.reminderCount.toString),
        note = note
      )

  def details(registration: DictionaryNamespaceRegistration): Map[String, String] =
    details(
      contextClubId = registration.contextClubId,
      ownerPlayerId = registration.ownerPlayerId,
      coOwnerPlayerIds = registration.coOwnerPlayerIds,
      editorPlayerIds = registration.editorPlayerIds
    )

  def details(
      contextClubId: Option[ClubId],
      ownerPlayerId: PlayerId,
      coOwnerPlayerIds: Vector[PlayerId],
      editorPlayerIds: Vector[PlayerId]
  ): Map[String, String] =
    Map(
      "contextClubId" -> contextClubId.map(_.value).getOrElse(""),
      "ownerPlayerId" -> ownerPlayerId.value,
      "coOwnerPlayerIds" -> coOwnerPlayerIds.map(_.value).mkString(","),
      "editorPlayerIds" -> editorPlayerIds.map(_.value).mkString(",")
    )
