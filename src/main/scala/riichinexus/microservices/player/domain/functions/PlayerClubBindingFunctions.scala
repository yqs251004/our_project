package riichinexus.microservices.player.domain.functions

import riichinexus.domain.model.ClubId
import riichinexus.microservices.player.domain.Player

object PlayerClubBindingFunctions:
  def boundClubIds(player: Player): Vector[ClubId] =
    (player.clubId.toVector ++ player.affiliatedClubIds).distinct

  def joinClub(player: Player, newClubId: ClubId): Player =
    val updatedBoundClubs = (boundClubIds(player) :+ newClubId).distinct
    val nextPrimaryClubId = player.clubId.orElse(Some(newClubId))
    player.copy(
      clubId = nextPrimaryClubId,
      affiliatedClubIds = updatedBoundClubs.filterNot(nextPrimaryClubId.contains)
    )

  def leaveClub(player: Player, existingClubId: ClubId): Player =
    val remaining = boundClubIds(player).filterNot(_ == existingClubId)
    player.copy(
      clubId = remaining.headOption,
      affiliatedClubIds = remaining.drop(1)
    )

  def leavePrimaryClub(player: Player): Player =
    player.clubId match
      case Some(primaryClubId) => leaveClub(player, primaryClubId)
      case None                => player.copy(affiliatedClubIds = Vector.empty)
