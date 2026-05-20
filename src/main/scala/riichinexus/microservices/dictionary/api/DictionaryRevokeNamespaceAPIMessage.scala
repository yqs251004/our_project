package riichinexus.microservices.dictionary.api

import cats.effect.IO

import java.time.Instant
import java.util.NoSuchElementException

import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.domain.model.*
import riichinexus.domain.service.GlobalDictionaryRegistry
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.dictionary.objects.apiTypes.*
import upickle.default.*

final case class DictionaryRevokeNamespaceAPIMessage(
    operatorId: String,
    namespacePrefix: String,
    note: Option[String] = None
) extends APIMessage[DictionaryNamespaceRegistration] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[DictionaryNamespaceRegistration] =
    IO {
      val module = context.support.dictionaryModule
      val request = RevokeDictionaryNamespaceRequest(operatorId, namespacePrefix, note)
      val actor = context.support.principal(request.operator)
      val revokedAt = Instant.now()

      module.transactionManager.inTransaction {
        module.authorizationService.requirePermission(actor, Permission.ManageGlobalDictionary)
        val reviewer = actor.playerId.getOrElse(PlayerId("system"))
        val normalizedPrefix = GlobalDictionaryRegistry.normalizeNamespacePrefix(request.namespacePrefix)
        module.tables.findNamespaceByPrefix(normalizedPrefix).map { existing =>
          val revoked = existing.revoke(reviewer, revokedAt, request.note)
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
              note = request.note
            )
          )
          revoked
        }.getOrElse(throw NoSuchElementException("Resource not found"))
      }
    }
