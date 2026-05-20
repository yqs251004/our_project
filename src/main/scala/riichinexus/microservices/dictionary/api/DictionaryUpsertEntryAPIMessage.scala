package riichinexus.microservices.dictionary.api

import cats.effect.IO

import java.time.Instant

import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.domain.event.GlobalDictionaryUpdated
import riichinexus.domain.model.*
import riichinexus.domain.service.GlobalDictionaryRegistry
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.dictionary.objects.apiTypes.*
import upickle.default.*

final case class DictionaryUpsertEntryAPIMessage(
    operatorId: String,
    key: String,
    value: String,
    note: Option[String] = None
) extends APIMessage[GlobalDictionaryEntry] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[GlobalDictionaryEntry] =
    IO {
      val module = context.support.dictionaryModule
      val request = UpsertDictionaryRequest(operatorId, key, value, note)
      val actor = context.support.principal(request.operator)
      val updatedAt = Instant.now()

      module.transactionManager.inTransaction {
        require(request.key.trim.nonEmpty, "Dictionary key cannot be empty")
        require(request.value.trim.nonEmpty, "Dictionary value cannot be empty")
        GlobalDictionaryRegistry.validate(request.key, request.value)

        if GlobalDictionaryRegistry.isMetadataKey(request.key) then
          val namespace = approvedMetadataNamespaceForKey(context, request.key).getOrElse(
            throw IllegalArgumentException(
              s"Metadata key ${request.key} requires an approved namespace registration such as ${GlobalDictionaryRegistry.metadataNamespacePrefixForKey(request.key)}"
            )
          )
          val actorId = actor.playerId.getOrElse(
            throw IllegalArgumentException("Metadata dictionary writes require a registered player identity")
          )
          if !actor.isSuperAdmin then
            requireNamespaceWriterActor(context, actorId, namespace, s"write ${request.key.trim}")
        else
          module.authorizationService.requirePermission(actor, Permission.ManageGlobalDictionary)

        val existingVersion = module.tables.findEntryByKey(request.key).map(_.version).getOrElse(0)
        val entry = GlobalDictionaryEntry(
          key = request.key,
          value = request.value,
          updatedAt = updatedAt,
          updatedBy = actor.playerId.getOrElse(PlayerId("system")),
          note = request.note,
          version = existingVersion
        )

        val saved = module.globalDictionaryRepository.save(entry)
        module.auditEventRepository.save(
          AuditEventEntry(
            id = IdGenerator.auditEventId(),
            aggregateType = "dictionary",
            aggregateId = request.key,
            eventType = "GlobalDictionaryUpserted",
            occurredAt = updatedAt,
            actorId = Some(entry.updatedBy),
            details = Map("key" -> request.key, "value" -> request.value),
            note = request.note
          )
        )
        module.eventBus.publish(GlobalDictionaryUpdated(saved, updatedAt))
        saved
      }
    }

  private def approvedMetadataNamespaceForKey(
      context: ApiPlanContext,
      key: String
  ): Option[DictionaryNamespaceRegistration] =
    val normalizedKey = GlobalDictionaryRegistry.normalizeKey(key)
    context.support.dictionaryModule.tables.listApprovedNamespaces()
      .filter(registration => normalizedKey.startsWith(registration.namespacePrefix))
      .sortBy(_.namespacePrefix.length)
      .lastOption

  private def requireNamespaceWriterActor(
      context: ApiPlanContext,
      actorId: PlayerId,
      registration: DictionaryNamespaceRegistration,
      action: String
  ): Unit =
    if !registration.hasWriteAccess(actorId) then
      throw IllegalArgumentException(
        s"Metadata namespace ${registration.namespacePrefix} is writable only by its owners/editors"
      )
    registration.contextClubId.foreach { clubId =>
      val player = requireActiveNamespaceOwner(context, actorId, s"$action writer ${actorId.value}")
      if !player.boundClubIds.contains(clubId) then
        throw IllegalArgumentException(
          s"Dictionary namespace $action writer ${actorId.value} requires ${actorId.value} to belong to context club ${clubId.value}"
        )
    }

  private def requireActiveNamespaceOwner(
      context: ApiPlanContext,
      playerId: PlayerId,
      action: String
  ): Player =
    context.support.dictionaryModule.playerRepository.findById(playerId) match
      case Some(player) if player.status == PlayerStatus.Active => player
      case Some(player) =>
        throw IllegalArgumentException(
          s"Dictionary namespace $action requires an active player owner, but ${playerId.value} is ${player.status.toString.toLowerCase}"
        )
      case None =>
        throw IllegalArgumentException(
          s"Dictionary namespace $action requires an existing player owner, but ${playerId.value} was not found"
        )
