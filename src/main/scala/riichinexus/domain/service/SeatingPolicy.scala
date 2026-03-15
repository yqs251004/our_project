package riichinexus.domain.service

import riichinexus.domain.model.*

final case class PlannedTable(
    tableNo: Int,
    seats: Vector[TableSeat]
) derives CanEqual

trait SeatingPolicy:
  def assignTables(
      players: Vector[Player],
      stage: TournamentStage,
      historicalRecords: Vector[MatchRecord] = Vector.empty
  ): Vector[PlannedTable]

final class BalancedEloSeatingPolicy extends SeatingPolicy:
  override def assignTables(
      players: Vector[Player],
      stage: TournamentStage,
      historicalRecords: Vector[MatchRecord]
  ): Vector[PlannedTable] =
    require(players.nonEmpty, s"Stage ${stage.name} needs players before scheduling")
    require(players.size % 4 == 0, "Player count must be divisible by 4 to schedule riichi tables")
    val representedClubByPlayer = representativeClubMap(stage)
    val preferredWindByPlayer = preferredWindMap(stage)

    val sortedPlayers = players
      .sortBy(player =>
        (
          -player.elo,
          representedClubByPlayer
            .get(player.id)
            .orElse(player.clubId)
            .map(_.value)
            .getOrElse("zzzzzz"),
          player.nickname
        )
      )

    val opponentCounts = buildOpponentCounts(historicalRecords)
    val groupedPlayers = buildOptimalGroups(sortedPlayers, opponentCounts)

    groupedPlayers.zipWithIndex
      .map { case (group, index) =>
        val rotatedGroup = rotate(orderSeatsWithinTable(group, opponentCounts), index % group.size)
        PlannedTable(
          tableNo = index + 1,
          seats = assignSeats(rotatedGroup, preferredWindByPlayer, representedClubByPlayer)
        )
      }
      .toVector

  private def buildOptimalGroups(
      players: Vector[Player],
      opponentCounts: Map[(PlayerId, PlayerId), Int]
  ): Vector[Vector[Player]] =
    val branchLimit =
      if players.size <= 8 then 12
      else if players.size <= 16 then 10
      else 8

    var bestScore = Double.MaxValue
    var bestGrouping = Vector.empty[Vector[Player]]

    def search(
        remaining: Vector[Player],
        current: Vector[Vector[Player]],
        currentScore: Double
    ): Unit =
      if remaining.isEmpty then
        if currentScore < bestScore then
          bestScore = currentScore
          bestGrouping = current
      else
        val anchor = selectAnchor(remaining, opponentCounts)
        val rest = remaining.filterNot(_.id == anchor.id)
        val candidates = rest.combinations(3).map(combo => (anchor +: combo.toVector)).toVector
          .sortBy(groupScore(_, opponentCounts))
          .take(branchLimit)

        candidates.foreach { group =>
          val penalty = groupScore(group, opponentCounts)
          val nextScore = currentScore + penalty
          if nextScore < bestScore then
            val groupedIds = group.map(_.id).toSet
            val nextRemaining = remaining.filterNot(player => groupedIds.contains(player.id))
            search(nextRemaining, current :+ group, nextScore)
        }

    search(players, Vector.empty, 0.0)

    if bestGrouping.nonEmpty then bestGrouping
    else players.grouped(4).map(_.toVector).toVector

  private def selectAnchor(
      players: Vector[Player],
      opponentCounts: Map[(PlayerId, PlayerId), Int]
  ): Player =
    players.maxBy { player =>
      val clubPressure = players.count(other => other.id != player.id && sameClub(player, other))
      val rematchPressure = players.filterNot(_.id == player.id).map(other => opponentCount(player.id, other.id, opponentCounts)).sum
      val flexibilityPenalty = player.boundClubIds.size
      (clubPressure, rematchPressure, flexibilityPenalty, player.elo)
    }

  private def groupScore(
      group: Vector[Player],
      opponentCounts: Map[(PlayerId, PlayerId), Int]
  ): Double =
    val clubPenalty = group.combinations(2).map {
      pair =>
        if pair.size == 2 && sameClub(pair(0), pair(1)) then 120.0 else 0.0
    }.sum
    val rematchPenalty = group.combinations(2).map {
      pair =>
        val count = opponentCount(pair(0).id, pair(1).id, opponentCounts)
        count.toDouble * 45.0
    }.sum
    val eloSpreadPenalty =
      if group.isEmpty then 0.0 else (group.map(_.elo).max - group.map(_.elo).min).toDouble / 50.0
    val clubConcentrationPenalty = group
      .flatMap(player => player.boundClubIds.map(_ -> player.id))
      .groupBy(_._1)
      .values
      .map(entries => math.pow(entries.map(_._2).distinct.size - 1, 2) * 12.0)
      .sum

    clubPenalty + rematchPenalty + eloSpreadPenalty + clubConcentrationPenalty

  private def buildOpponentCounts(
      historicalRecords: Vector[MatchRecord]
  ): Map[(PlayerId, PlayerId), Int] =
    historicalRecords.foldLeft(Map.empty[(PlayerId, PlayerId), Int]) { (acc, record) =>
      record.playerIds.combinations(2).foldLeft(acc) { (nestedAcc, pair) =>
        val key = pairKey(pair(0), pair(1))
        nestedAcc.updated(key, nestedAcc.getOrElse(key, 0) + 1)
      }
    }

  private def orderSeatsWithinTable(
      group: Vector[Player],
      opponentCounts: Map[(PlayerId, PlayerId), Int]
  ): Vector[Player] =
    group.sortBy(player =>
      (
        group.count(other => other.id != player.id && sameClub(player, other)),
        group.filterNot(_.id == player.id).map(other => opponentCount(player.id, other.id, opponentCounts)).sum,
        -player.elo,
        player.nickname
      )
    )

  private def sameClub(left: Player, right: Player): Boolean =
    left.boundClubIds.intersect(right.boundClubIds).nonEmpty

  private def opponentCount(
      left: PlayerId,
      right: PlayerId,
      opponentCounts: Map[(PlayerId, PlayerId), Int]
  ): Int =
    opponentCounts.getOrElse(pairKey(left, right), 0)

  private def pairKey(left: PlayerId, right: PlayerId): (PlayerId, PlayerId) =
    if left.value <= right.value then (left, right) else (right, left)

  private def representativeClubMap(stage: TournamentStage): Map[PlayerId, ClubId] =
    val pairings = stage.lineupSubmissions.flatMap { submission =>
      submission.seats.map(_.playerId -> submission.clubId)
    }
    val duplicatedAssignments = pairings
      .groupBy(_._1)
      .collect {
        case (playerId, assignments)
            if assignments.map(_._2).distinct.size > 1 =>
          playerId.value
      }
      .toVector

    require(
      duplicatedAssignments.isEmpty,
      s"Players cannot represent multiple clubs in the same stage: ${duplicatedAssignments.mkString(", ")}"
    )

    pairings.toMap

  private def preferredWindMap(stage: TournamentStage): Map[PlayerId, SeatWind] =
    stage.lineupSubmissions
      .flatMap(_.seats)
      .flatMap(seat => seat.preferredWind.map(_ -> seat.playerId))
      .groupBy(_._2)
      .map { case (playerId, preferences) =>
        val preferredWinds = preferences.map(_._1).distinct
        require(
          preferredWinds.size <= 1,
          s"Player ${playerId.value} cannot declare multiple preferred winds in the same stage"
        )
        playerId -> preferredWinds.head
      }

  private def assignSeats(
      players: Vector[Player],
      preferredWindByPlayer: Map[PlayerId, SeatWind],
      representedClubByPlayer: Map[PlayerId, ClubId]
  ): Vector[TableSeat] =
    val baselineOrder = players.zipWithIndex.map { case (player, index) => player.id -> index }.toMap
    val chosenPlayers =
      players.permutations.minBy { candidate =>
        val preferencePenalty = SeatWind.all.zip(candidate).count { case (seat, player) =>
          preferredWindByPlayer.get(player.id).exists(_ != seat)
        }
        val displacementPenalty = candidate.zipWithIndex.map { case (player, index) =>
          math.abs(index - baselineOrder(player.id))
        }.sum
        val tieBreaker = candidate.map(_.nickname).mkString("|")
        (preferencePenalty, displacementPenalty, tieBreaker)
      }.toVector

    SeatWind.all.zip(chosenPlayers).map { case (seat, player) =>
      TableSeat(
        seat,
        player.id,
        clubId = representedClubByPlayer.get(player.id).orElse(player.clubId)
      )
    }

  private def rotate[A](values: Vector[A], shift: Int): Vector[A] =
    if values.isEmpty then values
    else
      val normalized = shift % values.size
      values.drop(normalized) ++ values.take(normalized)
