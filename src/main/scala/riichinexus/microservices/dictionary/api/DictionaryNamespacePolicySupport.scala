package riichinexus.microservices.dictionary.api

import riichinexus.application.ports.{ClubRepository, PlayerRepository}
import riichinexus.domain.model.*

private[api] final class DictionaryNamespacePolicySupport(
    playerRepository: PlayerRepository,
    clubRepository: ClubRepository
):
  def requireActiveNamespaceOwner(playerId: PlayerId, context: String): Player =
    playerRepository.findById(playerId) match
      case Some(player) if player.status == PlayerStatus.Active => player
      case Some(player) =>
        throw IllegalArgumentException(
          s"Dictionary namespace $context requires an active player owner, but ${playerId.value} is ${player.status.toString.toLowerCase}"
        )
      case None =>
        throw IllegalArgumentException(
          s"Dictionary namespace $context requires an existing player owner, but ${playerId.value} was not found"
        )

  def requireExistingNamespaceContextClub(clubId: ClubId, context: String): Club =
    clubRepository.findById(clubId).getOrElse(
      throw IllegalArgumentException(
        s"Dictionary namespace $context requires an existing context club, but ${clubId.value} was not found"
      )
    )

  def requireNamespaceContextMembership(
      player: Player,
      contextClubId: ClubId,
      context: String
  ): Unit =
    if !player.boundClubIds.contains(contextClubId) then
      throw IllegalArgumentException(
        s"Dictionary namespace $context requires ${player.id.value} to belong to context club ${contextClubId.value}"
      )

  def validateNamespaceContextMembership(
      contextClubId: Option[ClubId],
      owner: Player,
      coOwnerPlayerIds: Vector[PlayerId],
      editorPlayerIds: Vector[PlayerId],
      context: String
  ): Option[ClubId] =
    contextClubId.map { clubId =>
      requireExistingNamespaceContextClub(clubId, context)
      requireNamespaceContextMembership(owner, clubId, s"$context owner ${owner.id.value}")
      coOwnerPlayerIds.foreach { playerId =>
        val player = requireActiveNamespaceOwner(playerId, s"$context co-owner ${playerId.value}")
        requireNamespaceContextMembership(player, clubId, s"$context co-owner ${playerId.value}")
      }
      editorPlayerIds.foreach { playerId =>
        val player = requireActiveNamespaceOwner(playerId, s"$context editor ${playerId.value}")
        requireNamespaceContextMembership(player, clubId, s"$context editor ${playerId.value}")
      }
      clubId
    }

  def normalizeNamespaceCollaborators(
      ownerPlayerId: PlayerId,
      coOwnerPlayerIds: Vector[PlayerId],
      editorPlayerIds: Vector[PlayerId],
      context: String
  ): (Vector[PlayerId], Vector[PlayerId]) =
    val normalizedCoOwners = coOwnerPlayerIds.distinct.filterNot(_ == ownerPlayerId)
    normalizedCoOwners.foreach(playerId => requireActiveNamespaceOwner(playerId, s"$context co-owner ${playerId.value}"))
    val normalizedEditors =
      editorPlayerIds.distinct.filterNot(playerId => playerId == ownerPlayerId || normalizedCoOwners.contains(playerId))
    normalizedEditors.foreach(playerId => requireActiveNamespaceOwner(playerId, s"$context editor ${playerId.value}"))
    (normalizedCoOwners, normalizedEditors)

  def requireNamespaceManagementActor(
      actor: AccessPrincipal,
      registration: DictionaryNamespaceRegistration,
      context: String
  ): PlayerId =
    if actor.isSuperAdmin then actor.playerId.getOrElse(PlayerId("system"))
    else
      val actorId = actor.playerId.getOrElse(
        throw IllegalArgumentException(s"Dictionary namespace $context requires a registered player identity")
      )
      if registration.hasOwnership(actorId) then actorId
      else
        throw IllegalArgumentException(
          s"Dictionary namespace ${registration.namespacePrefix} can only be managed by a super admin or one of its owners"
        )

  def requireNamespaceWriterActor(
      actorId: PlayerId,
      registration: DictionaryNamespaceRegistration,
      context: String
  ): Unit =
    if !registration.hasWriteAccess(actorId) then
      throw IllegalArgumentException(
        s"Metadata namespace ${registration.namespacePrefix} is writable only by its owners/editors"
      )
    registration.contextClubId.foreach { clubId =>
      val player = requireActiveNamespaceOwner(actorId, s"$context writer ${actorId.value}")
      requireNamespaceContextMembership(player, clubId, s"$context writer ${actorId.value}")
    }
