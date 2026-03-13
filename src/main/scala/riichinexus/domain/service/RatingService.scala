package riichinexus.domain.service

import riichinexus.domain.model.*

final case class RatingChange(
    playerId: PlayerId,
    delta: Int
) derives CanEqual

trait RatingService:
  def calculateDeltas(players: Vector[Player], standings: Vector[FinalStanding]): Vector[RatingChange]

final class PairwiseEloRatingService(kFactor: Int = 36) extends RatingService:
  override def calculateDeltas(
      players: Vector[Player],
      standings: Vector[FinalStanding]
  ): Vector[RatingChange] =
    require(players.nonEmpty, "Cannot calculate rating deltas without players")
    require(
      players.map(_.id).toSet == standings.map(_.playerId).toSet,
      "Players and final standings must reference the same participants"
    )

    val placementByPlayer = standings.map(standing => standing.playerId -> standing.placement).toMap
    val rawDeltas = players.map { player =>
      val adjustment = players
        .filterNot(_.id == player.id)
        .map { opponent =>
          val actualScore =
            comparePlacements(
              placementByPlayer(player.id),
              placementByPlayer(opponent.id)
            )
          val expectedScore =
            1.0 / (1.0 + math.pow(10.0, (opponent.elo - player.elo) / 400.0))

          actualScore - expectedScore
        }
        .sum / (players.size - 1).toDouble

      player.id -> (kFactor * adjustment)
    }

    val rounded = rawDeltas.map { case (playerId, delta) =>
      RatingChange(playerId, math.round(delta).toInt)
    }

    val drift = rounded.map(_.delta).sum
    if drift == 0 || rounded.isEmpty then rounded
    else
      val last = rounded.last
      rounded.updated(rounded.size - 1, last.copy(delta = last.delta - drift))

  private def comparePlacements(playerPlacement: Int, opponentPlacement: Int): Double =
    if playerPlacement < opponentPlacement then 1.0
    else if playerPlacement > opponentPlacement then 0.0
    else 0.5
