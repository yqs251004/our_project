package riichinexus.microservices.tournament.domain.tablemanagement.functions

import riichinexus.microservices.tournament.domain.lineupmanagement.functions.*
import riichinexus.microservices.tournament.domain.paifumanagement.functions.*
import riichinexus.microservices.tournament.domain.recordmanagement.functions.*
import riichinexus.microservices.tournament.domain.rulesmanagement.functions.*
import riichinexus.microservices.tournament.domain.rulesmanagement.functions.knockout.*
import riichinexus.microservices.tournament.domain.rulesmanagement.functions.ranking.*
import riichinexus.microservices.tournament.domain.rulesmanagement.functions.stageprogression.*
import riichinexus.microservices.tournament.domain.rulesmanagement.functions.swiss.*
import riichinexus.microservices.tournament.domain.settlementmanagement.functions.*
import riichinexus.microservices.tournament.domain.tablemanagement.functions.*
import riichinexus.microservices.tournament.domain.tournamentmanagement.functions.*
import riichinexus.domain.model.*
import riichinexus.microservices.tournament.domain.recordmanagement.functions.MatchRecordFunctions
import riichinexus.microservices.tournament.domain.lineupmanagement.model.*
import riichinexus.microservices.tournament.domain.recordmanagement.model.*
import riichinexus.microservices.tournament.domain.settlementmanagement.model.*
import riichinexus.microservices.tournament.domain.tablemanagement.model.*
import riichinexus.microservices.tournament.domain.tournamentmanagement.model.*
import riichinexus.microservices.club.domain.model.*
import riichinexus.microservices.tournament.objects.tablemanagement.TableSeat
import riichinexus.microservices.club.objects.ClubRelationKind
import riichinexus.microservices.player.objects.Player
import riichinexus.microservices.tournament.objects.tablemanagement.SeatWind

object SeatingPolicy:
  def planTables(
      players: Vector[Player],
      stage: TournamentStage,
      roundNumber: Int,
      historicalRecords: Vector[MatchRecord] = Vector.empty,
      clubRelations: Map[(ClubId, ClubId), ClubRelationKind] = Map.empty
  ): Vector[StageTablePlan] =
    require(players.nonEmpty, s"Stage ${stage.name} needs players before scheduling")
    require(roundNumber >= 1, "Round number must be positive")
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
    val groupedPlayers = normalizedPairingMethod(stage) match
      case "balanced-elo" =>
        buildOptimalGroups(sortedPlayers, opponentCounts, representedClubByPlayer, clubRelations)
      case "snake"        => buildSnakeGroups(sortedPlayers)
      case method =>
        throw IllegalArgumentException(s"Unsupported swiss pairing method: $method")

    groupedPlayers.zipWithIndex
      .map { case (group, index) =>
        val rotatedGroup = rotate(
          orderSeatsWithinTable(group, opponentCounts, representedClubByPlayer, clubRelations),
          index % group.size
        )
        StageTablePlan(
          roundNumber = roundNumber,
          tableNo = index + 1,
          seats = assignSeats(rotatedGroup, preferredWindByPlayer, representedClubByPlayer)
        )
      }
      .toVector

  private def normalizedPairingMethod(stage: TournamentStage): String =
    stage.swissRule.map(_.pairingMethod.trim.toLowerCase).getOrElse("balanced-elo")

  private def buildSnakeGroups(players: Vector[Player]): Vector[Vector[Player]] =
    val tableCount = players.size / 4
    players.grouped(tableCount).zipWithIndex
      .flatMap { case (row, rowIndex) =>
        val tableIndices =
          if rowIndex % 2 == 0 then row.indices
          else row.indices.reverse

        tableIndices.zip(row).map { case (tableIndex, player) =>
          tableIndex -> player
        }
      }
      .toVector
      .groupBy(_._1)
      .toVector
      .sortBy(_._1)
      .map { case (_, entries) => entries.map(_._2) }

  private def buildOptimalGroups(
      players: Vector[Player],
      opponentCounts: Map[(PlayerId, PlayerId), Int],
      representedClubByPlayer: Map[PlayerId, ClubId],
      clubRelations: Map[(ClubId, ClubId), ClubRelationKind]
  ): Vector[Vector[Player]] =
    final case class SearchResult(score: Double, grouping: Vector[Vector[Player]])

    val branchLimit =
      if players.size <= 8 then 12
      else if players.size <= 16 then 10
      else 8

    def search(
        remaining: Vector[Player],
        current: Vector[Vector[Player]],
        currentScore: Double,
        best: SearchResult
    ): SearchResult =
      if remaining.isEmpty then
        if currentScore < best.score then SearchResult(currentScore, current) else best
      else
        val anchor = selectAnchor(remaining, opponentCounts, representedClubByPlayer, clubRelations)
        val rest = remaining.filterNot(_.id == anchor.id)
        val candidates = rest.combinations(3).map(combo => (anchor +: combo.toVector)).toVector
          .sortBy(groupScore(_, opponentCounts, representedClubByPlayer, clubRelations))
          .take(branchLimit)

        candidates.foldLeft(best) { (currentBest, group) =>
          val penalty = groupScore(group, opponentCounts, representedClubByPlayer, clubRelations)
          val nextScore = currentScore + penalty
          if nextScore < currentBest.score then
            val groupedIds = group.map(_.id).toSet
            val nextRemaining = remaining.filterNot(player => groupedIds.contains(player.id))
            search(nextRemaining, current :+ group, nextScore, currentBest)
          else currentBest
        }

    val best = search(
      remaining = players,
      current = Vector.empty,
      currentScore = 0.0,
      best = SearchResult(Double.MaxValue, Vector.empty)
    )

    if best.grouping.nonEmpty then best.grouping
    else players.grouped(4).map(_.toVector).toVector

  private def selectAnchor(
      players: Vector[Player],
      opponentCounts: Map[(PlayerId, PlayerId), Int],
      representedClubByPlayer: Map[PlayerId, ClubId],
      clubRelations: Map[(ClubId, ClubId), ClubRelationKind]
  ): Player =
    players.maxBy { player =>
      val clubPressure = players.count(other =>
        other.id != player.id && sameClub(player, other, representedClubByPlayer)
      )
      val relationPressure = players.filterNot(_.id == player.id).count(other =>
        relationBetween(player, other, representedClubByPlayer, clubRelations).nonEmpty
      )
      val rematchPressure = players.filterNot(_.id == player.id).map(other => opponentCount(player.id, other.id, opponentCounts)).sum
      val flexibilityPenalty = player.boundClubIds.size
      (clubPressure, relationPressure, rematchPressure, flexibilityPenalty, player.elo)
    }

  private def groupScore(
      group: Vector[Player],
      opponentCounts: Map[(PlayerId, PlayerId), Int],
      representedClubByPlayer: Map[PlayerId, ClubId],
      clubRelations: Map[(ClubId, ClubId), ClubRelationKind]
  ): Double =
    val clubPenalty = group.combinations(2).map {
      pair =>
        if pair.size == 2 && sameClub(pair(0), pair(1), representedClubByPlayer) then 120.0 else 0.0
    }.sum
    val relationPenalty = group.combinations(2).map {
      pair =>
        relationBetween(pair(0), pair(1), representedClubByPlayer, clubRelations) match
          case Some(ClubRelationKind.Alliance) => 70.0
          case Some(ClubRelationKind.Rivalry)  => -18.0
          case _                               => 0.0
    }.sum
    val rematchPenalty = group.combinations(2).map {
      pair =>
        val count = opponentCount(pair(0).id, pair(1).id, opponentCounts)
        count.toDouble * 45.0
    }.sum
    val eloSpreadPenalty =
      if group.isEmpty then 0.0 else (group.map(_.elo).max - group.map(_.elo).min).toDouble / 50.0
    val clubConcentrationPenalty = group
      .flatMap(player => representedClubs(player, representedClubByPlayer).map(_ -> player.id))
      .groupBy(_._1)
      .values
      .map(entries => math.pow(entries.map(_._2).distinct.size - 1, 2) * 12.0)
      .sum

    clubPenalty + relationPenalty + rematchPenalty + eloSpreadPenalty + clubConcentrationPenalty

  private def buildOpponentCounts(
      historicalRecords: Vector[MatchRecord]
  ): Map[(PlayerId, PlayerId), Int] =
    historicalRecords.foldLeft(Map.empty[(PlayerId, PlayerId), Int]) { (acc, record) =>
      MatchRecordFunctions.playerIds(record).combinations(2).foldLeft(acc) { (nestedAcc, pair) =>
        val key = pairKey(pair(0), pair(1))
        nestedAcc.updated(key, nestedAcc.getOrElse(key, 0) + 1)
      }
    }

  private def orderSeatsWithinTable(
      group: Vector[Player],
      opponentCounts: Map[(PlayerId, PlayerId), Int],
      representedClubByPlayer: Map[PlayerId, ClubId],
      clubRelations: Map[(ClubId, ClubId), ClubRelationKind]
  ): Vector[Player] =
    group.sortBy(player =>
      (
        group.count(other => other.id != player.id && sameClub(player, other, representedClubByPlayer)),
        group.count(other =>
          other.id != player.id &&
            relationBetween(player, other, representedClubByPlayer, clubRelations).contains(ClubRelationKind.Alliance)
        ),
        group.filterNot(_.id == player.id).map(other => opponentCount(player.id, other.id, opponentCounts)).sum,
        -player.elo,
        player.nickname
      )
    )

  private def sameClub(
      left: Player,
      right: Player,
      representedClubByPlayer: Map[PlayerId, ClubId]
  ): Boolean =
    representedClubs(left, representedClubByPlayer).intersect(representedClubs(right, representedClubByPlayer)).nonEmpty

  private def representedClubs(
      player: Player,
      representedClubByPlayer: Map[PlayerId, ClubId]
  ): Vector[ClubId] =
    representedClubByPlayer.get(player.id).toVector ++
      player.boundClubIds.filterNot(representedClubByPlayer.get(player.id).contains)

  private def relationBetween(
      left: Player,
      right: Player,
      representedClubByPlayer: Map[PlayerId, ClubId],
      clubRelations: Map[(ClubId, ClubId), ClubRelationKind]
  ): Option[ClubRelationKind] =
    representedClubs(left, representedClubByPlayer)
      .flatMap(leftClub =>
        representedClubs(right, representedClubByPlayer).flatMap { rightClub =>
          if leftClub == rightClub then None
          else clubRelations.get(clubPairKey(leftClub, rightClub))
        }
      )
      .sortBy {
        case ClubRelationKind.Alliance => 0
        case ClubRelationKind.Rivalry  => 1
        case ClubRelationKind.Neutral  => 2
      }
      .headOption

  private def opponentCount(
      left: PlayerId,
      right: PlayerId,
      opponentCounts: Map[(PlayerId, PlayerId), Int]
  ): Int =
    opponentCounts.getOrElse(pairKey(left, right), 0)

  private def pairKey(left: PlayerId, right: PlayerId): (PlayerId, PlayerId) =
    if left.value <= right.value then (left, right) else (right, left)

  private def clubPairKey(left: ClubId, right: ClubId): (ClubId, ClubId) =
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
