package riichinexus.microservices.tournament.domain.stage.functions.lineup


import riichinexus.microservices.player.objects.PlayerId
import riichinexus.microservices.club.objects.profile.ClubId
import riichinexus.microservices.tournament.domain.stage.model.TournamentStage
import riichinexus.microservices.tournament.domain.competition.model.Tournament
import riichinexus.microservices.player.objects.`private`.PlayerPrivateView

/** StageLineupResolver 负责阶段阵容解析器 相关的领域编排、构建或投影计算。 */

private[tournament] object StageLineupResolver:
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
      playerLookup: PlayerId => Option[PlayerPrivateView]
  ): Vector[PlayerId] =
    val resolvedBySubmission = stage.lineupSubmissions.flatMap { submission =>
      val activeSeats = submission.seats.filterNot(_.reserve)
      val reserveSeats = submission.seats.filter(_.reserve)

      val availableActive = activeSeats.flatMap { seat =>
        playerLookup(seat.playerId).filter(_.active).map(_ => seat.playerId)
      }
      val promotedReserves = reserveSeats
        .filterNot(seat => availableActive.contains(seat.playerId))
        .flatMap { seat =>
          playerLookup(seat.playerId).filter(_.active).map(_ => seat.playerId)
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
        playerLookup(playerId).filter(_.active).map(_ => playerId)
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
