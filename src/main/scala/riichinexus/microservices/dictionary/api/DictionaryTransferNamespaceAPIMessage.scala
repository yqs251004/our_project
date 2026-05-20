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

final case class DictionaryTransferNamespaceAPIMessage(
    operatorId: String,
    namespacePrefix: String,
    newOwnerPlayerId: String,
    note: Option[String] = None
) extends APIMessage[DictionaryNamespaceRegistration] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[DictionaryNamespaceRegistration] =
    IO {
      val module = context.support.dictionaryModule
      val request = TransferDictionaryNamespaceRequest(operatorId, namespacePrefix, newOwnerPlayerId, note)
      val actor = context.support.principal(request.operator)
      val transferredAt = Instant.now()

      module.transactionManager.inTransaction {
        module.authorizationService.requirePermission(actor, Permission.ManageGlobalDictionary)
        val reviewer = actor.playerId.getOrElse(PlayerId("system"))
        val newOwner = requireActiveNamespaceOwner(context, request.newOwner, s"transfer ownership to ${request.newOwner.value}")
        val normalizedPrefix = GlobalDictionaryRegistry.normalizeNamespacePrefix(request.namespacePrefix)
        module.tables.findNamespaceByPrefix(normalizedPrefix).map { existing =>
          existing.contextClubId.foreach { clubId =>
            requireNamespaceContextMembership(
              newOwner,
              clubId,
              s"transfer ownership for $normalizedPrefix to ${request.newOwner.value}"
            )
          }
          val transferred = existing.transferOwnership(request.newOwner, reviewer, transferredAt, request.note)
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
                "ownerPlayerId" -> request.newOwner.value,
                "coOwnerPlayerIds" -> transferred.coOwnerPlayerIds.map(_.value).mkString(","),
                "editorPlayerIds" -> transferred.editorPlayerIds.map(_.value).mkString(",")
              ),
              note = request.note
            )
          )
          transferred
        }.getOrElse(throw NoSuchElementException("Resource not found"))
      }
    }

  private def requireActiveNamespaceOwner(context: ApiPlanContext, playerId: PlayerId, action: String): Player =
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

  private def requireNamespaceContextMembership(player: Player, contextClubId: ClubId, action: String): Unit =
    if !player.boundClubIds.contains(contextClubId) then
      throw IllegalArgumentException(
        s"Dictionary namespace $action requires ${player.id.value} to belong to context club ${contextClubId.value}"
      )
