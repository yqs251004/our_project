package riichinexus.microservices.dictionary.domain

import java.util.NoSuchElementException

import riichinexus.bootstrap.DictionaryModuleContext
import riichinexus.domain.model.*

private[dictionary] object DictionaryNamespaceValidation:
  def requireNamespace(
      module: DictionaryModuleContext,
      namespacePrefix: String
  ): DictionaryNamespaceRegistration =
    module.tables.findNamespaceByPrefix(namespacePrefix)
      .getOrElse(throw NoSuchElementException("Resource not found"))

  def requireManagementActor(
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

  def requireActiveOwner(
      module: DictionaryModuleContext,
      playerId: PlayerId,
      action: String
  ): Player =
    module.playerRepository.findById(playerId) match
      case Some(player) if player.status == PlayerStatus.Active => player
      case Some(player) =>
        throw IllegalArgumentException(
          s"Dictionary namespace $action requires an active player owner, but ${playerId.value} is ${player.status.toString.toLowerCase}"
        )
      case None =>
        throw IllegalArgumentException(
          s"Dictionary namespace $action requires an existing player owner, but ${playerId.value} was not found"
        )

  def validateContextMembership(
      module: DictionaryModuleContext,
      contextClubId: Option[ClubId],
      owner: Player,
      coOwnerPlayerIds: Vector[PlayerId],
      editorPlayerIds: Vector[PlayerId],
      action: String
  ): Option[ClubId] =
    contextClubId.map { clubId =>
      module.clubRepository.findById(clubId).getOrElse(
        throw IllegalArgumentException(
          s"Dictionary namespace $action requires an existing context club, but ${clubId.value} was not found"
        )
      )
      requireContextMembership(owner, clubId, s"$action owner ${owner.id.value}")
      coOwnerPlayerIds.foreach { playerId =>
        val player = requireActiveOwner(module, playerId, s"$action co-owner ${playerId.value}")
        requireContextMembership(player, clubId, s"$action co-owner ${playerId.value}")
      }
      editorPlayerIds.foreach { playerId =>
        val player = requireActiveOwner(module, playerId, s"$action editor ${playerId.value}")
        requireContextMembership(player, clubId, s"$action editor ${playerId.value}")
      }
      clubId
    }

  def requireContextMembership(player: Player, contextClubId: ClubId, action: String): Unit =
    if !player.boundClubIds.contains(contextClubId) then
      throw IllegalArgumentException(
        s"Dictionary namespace $action requires ${player.id.value} to belong to context club ${contextClubId.value}"
      )

  def normalizeCollaborators(
      module: DictionaryModuleContext,
      ownerPlayerId: PlayerId,
      coOwnerPlayerIds: Vector[PlayerId],
      editorPlayerIds: Vector[PlayerId],
      action: String
  ): (Vector[PlayerId], Vector[PlayerId]) =
    val normalizedCoOwners = coOwnerPlayerIds.distinct.filterNot(_ == ownerPlayerId)
    normalizedCoOwners.foreach(playerId => requireActiveOwner(module, playerId, s"$action co-owner ${playerId.value}"))
    val normalizedEditors =
      editorPlayerIds.distinct.filterNot(playerId => playerId == ownerPlayerId || normalizedCoOwners.contains(playerId))
    normalizedEditors.foreach(playerId => requireActiveOwner(module, playerId, s"$action editor ${playerId.value}"))
    (normalizedCoOwners, normalizedEditors)
