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

final case class DictionaryUpdateNamespaceContextAPIMessage(
    operatorId: String,
    namespacePrefix: String,
    contextClubId: Option[String] = None,
    note: Option[String] = None
) extends APIMessage[DictionaryNamespaceRegistration] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[DictionaryNamespaceRegistration] =
    IO {
      val module = context.support.dictionaryModule
      val request = UpdateDictionaryNamespaceContextRequest(operatorId, namespacePrefix, contextClubId, note)
      val actor = context.support.principal(request.operator)
      val updatedAt = Instant.now()

      module.transactionManager.inTransaction {
        val normalizedPrefix = GlobalDictionaryRegistry.normalizeNamespacePrefix(request.namespacePrefix)
        module.tables.findNamespaceByPrefix(normalizedPrefix).map { existing =>
          val reviewer = requireNamespaceManagementActor(actor, existing, s"update context for $normalizedPrefix")
          val owner = requireActiveNamespaceOwner(context, existing.ownerPlayerId, s"update context for $normalizedPrefix owner ${existing.ownerPlayerId.value}")
          val normalizedContextClubId = validateNamespaceContextMembership(
            context,
            request.contextClub,
            owner,
            existing.coOwnerPlayerIds,
            existing.editorPlayerIds,
            s"update context for $normalizedPrefix"
          )
          val updated = existing.updateContextClub(normalizedContextClubId, reviewer, updatedAt, request.note)
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
              note = request.note
            )
          )
          updated
        }.getOrElse(throw NoSuchElementException("Resource not found"))
      }
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

  private def validateNamespaceContextMembership(
      context: ApiPlanContext,
      contextClubId: Option[ClubId],
      owner: Player,
      coOwnerPlayerIds: Vector[PlayerId],
      editorPlayerIds: Vector[PlayerId],
      action: String
  ): Option[ClubId] =
    contextClubId.map { clubId =>
      context.support.dictionaryModule.clubRepository.findById(clubId).getOrElse(
        throw IllegalArgumentException(
          s"Dictionary namespace $action requires an existing context club, but ${clubId.value} was not found"
        )
      )
      requireNamespaceContextMembership(owner, clubId, s"$action owner ${owner.id.value}")
      coOwnerPlayerIds.foreach { playerId =>
        val player = requireActiveNamespaceOwner(context, playerId, s"$action co-owner ${playerId.value}")
        requireNamespaceContextMembership(player, clubId, s"$action co-owner ${playerId.value}")
      }
      editorPlayerIds.foreach { playerId =>
        val player = requireActiveNamespaceOwner(context, playerId, s"$action editor ${playerId.value}")
        requireNamespaceContextMembership(player, clubId, s"$action editor ${playerId.value}")
      }
      clubId
    }

  private def requireNamespaceContextMembership(player: Player, contextClubId: ClubId, action: String): Unit =
    if !player.boundClubIds.contains(contextClubId) then
      throw IllegalArgumentException(
        s"Dictionary namespace $action requires ${player.id.value} to belong to context club ${contextClubId.value}"
      )
