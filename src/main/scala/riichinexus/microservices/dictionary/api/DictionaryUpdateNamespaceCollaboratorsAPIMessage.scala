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

final case class DictionaryUpdateNamespaceCollaboratorsAPIMessage(
    operatorId: String,
    namespacePrefix: String,
    coOwnerPlayerIds: Vector[String] = Vector.empty,
    editorPlayerIds: Vector[String] = Vector.empty,
    note: Option[String] = None
) extends APIMessage[DictionaryNamespaceRegistration] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[DictionaryNamespaceRegistration] =
    IO {
      val module = context.support.dictionaryModule
      val request = UpdateDictionaryNamespaceCollaboratorsRequest(operatorId, namespacePrefix, coOwnerPlayerIds, editorPlayerIds, note)
      val actor = context.support.principal(request.operator)
      val updatedAt = Instant.now()

      module.transactionManager.inTransaction {
        val normalizedPrefix = GlobalDictionaryRegistry.normalizeNamespacePrefix(request.namespacePrefix)
        module.tables.findNamespaceByPrefix(normalizedPrefix).map { existing =>
          val reviewer = requireNamespaceManagementActor(actor, existing, s"update collaborators for $normalizedPrefix")
          val (normalizedCoOwners, normalizedEditors) = normalizeNamespaceCollaborators(
            context,
            existing.ownerPlayerId,
            request.coOwners,
            request.editors,
            s"update collaborators for $normalizedPrefix"
          )
          validateNamespaceContextMembership(
            context,
            existing.contextClubId,
            requireActiveNamespaceOwner(context, existing.ownerPlayerId, s"update collaborators for $normalizedPrefix owner ${existing.ownerPlayerId.value}"),
            normalizedCoOwners,
            normalizedEditors,
            s"update collaborators for $normalizedPrefix"
          )
          val updated = existing.updateCollaborators(normalizedCoOwners, normalizedEditors, reviewer, updatedAt, request.note)
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

  private def normalizeNamespaceCollaborators(
      context: ApiPlanContext,
      ownerPlayerId: PlayerId,
      coOwnerPlayerIds: Vector[PlayerId],
      editorPlayerIds: Vector[PlayerId],
      action: String
  ): (Vector[PlayerId], Vector[PlayerId]) =
    val normalizedCoOwners = coOwnerPlayerIds.distinct.filterNot(_ == ownerPlayerId)
    normalizedCoOwners.foreach(playerId => requireActiveNamespaceOwner(context, playerId, s"$action co-owner ${playerId.value}"))
    val normalizedEditors =
      editorPlayerIds.distinct.filterNot(playerId => playerId == ownerPlayerId || normalizedCoOwners.contains(playerId))
    normalizedEditors.foreach(playerId => requireActiveNamespaceOwner(context, playerId, s"$action editor ${playerId.value}"))
    (normalizedCoOwners, normalizedEditors)
