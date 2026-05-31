package riichinexus.microservices.tournament.domain.lineupmanagement.functions

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
import riichinexus.microservices.tournament.domain.lineupmanagement.model.*
import riichinexus.microservices.tournament.domain.recordmanagement.model.*
import riichinexus.microservices.tournament.domain.settlementmanagement.model.*
import riichinexus.microservices.tournament.domain.tablemanagement.model.*
import riichinexus.microservices.tournament.domain.tournamentmanagement.model.*
import riichinexus.microservices.player.objects.*

object StageLineupResolver:
  def directPlayerIds(tournament: Tournament): Vector[PlayerId] =
    (tournament.participatingPlayers ++ tournament.whitelist.flatMap(_.playerId)).distinct

  def resolveTargetPlayerIds(
      tournament: Tournament,
      stagePlayerIds: Vector[PlayerId],
      fallbackPlayerIds: Vector[PlayerId]
  ): Vector[PlayerId] =
    if stagePlayerIds.nonEmpty then
      (stagePlayerIds ++ directPlayerIds(tournament)).distinct
    else fallbackPlayerIds

  def submittedPlayersWithClub(
      stage: TournamentStage
  ): Vector[(PlayerId, ClubId)] =
    stage.lineupSubmissions.flatMap { submission =>
      submission.seats.map(_.playerId -> submission.clubId)
    }

  def resolveEligiblePlayers(
      stage: TournamentStage,
      playerLookup: PlayerId => Option[Player]
  ): Vector[PlayerId] =
    val resolvedBySubmission = stage.lineupSubmissions.flatMap { submission =>
      val activeSeats = submission.seats.filterNot(_.reserve)
      val reserveSeats = submission.seats.filter(_.reserve)

      val availableActive = activeSeats.flatMap { seat =>
        playerLookup(seat.playerId).filter(_.status == PlayerStatus.Active).map(_ => seat.playerId)
      }
      val promotedReserves = reserveSeats
        .filterNot(seat => availableActive.contains(seat.playerId))
        .flatMap { seat =>
          playerLookup(seat.playerId).filter(_.status == PlayerStatus.Active).map(_ => seat.playerId)
        }

      val shortfall = math.max(0, activeSeats.size - availableActive.size)
      availableActive ++ promotedReserves.take(shortfall)
    }

    val selected = resolvedBySubmission.distinct
    val reserveCandidates = stage.lineupSubmissions
      .flatMap(_.seats.filter(_.reserve).map(_.playerId))
      .distinct
      .filterNot(selected.contains)
      .flatMap { playerId =>
        playerLookup(playerId).filter(_.status == PlayerStatus.Active).map(_ => playerId)
      }

    val remainder = selected.size % 4
    if remainder == 0 then selected
    else
      val needed = 4 - remainder
      if reserveCandidates.size >= needed then selected ++ reserveCandidates.take(needed)
      else selected

  def effectiveRoundLimit(stage: TournamentStage): Int =
    stage.swissRule.flatMap(_.maxRounds) match
      case Some(limit) => math.max(1, math.min(stage.roundCount, limit))
      case None        => stage.roundCount
