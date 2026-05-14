package riichinexus.microservices.tournament.api

import riichinexus.application.ports.PlayerRepository
import riichinexus.domain.model.*

object StageLineupSupport:
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

  def resolveEligiblePlayers(
      stage: TournamentStage,
      playerRepository: PlayerRepository
  ): Vector[PlayerId] =
    resolveEligiblePlayers(stage, playerRepository.findById)

  def effectiveRoundLimit(stage: TournamentStage): Int =
    stage.swissRule.flatMap(_.maxRounds) match
      case Some(limit) => math.max(1, math.min(stage.roundCount, limit))
      case None        => stage.roundCount
