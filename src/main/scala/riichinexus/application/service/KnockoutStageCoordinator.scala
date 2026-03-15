package riichinexus.application.service

import java.time.Instant
import java.util.NoSuchElementException

import riichinexus.application.ports.*
import riichinexus.domain.model.*
import riichinexus.domain.service.TournamentRuleEngine

final class KnockoutStageCoordinator(
    tournamentRepository: TournamentRepository,
    playerRepository: PlayerRepository,
    clubRepository: ClubRepository,
    tableRepository: TableRepository,
    matchRecordRepository: MatchRecordRepository,
    tournamentRuleEngine: TournamentRuleEngine,
    transactionManager: TransactionManager = NoOpTransactionManager
):
  def buildProgression(
      tournamentId: TournamentId,
      stageId: TournamentStageId,
      at: Instant = Instant.now()
  ): KnockoutBracketSnapshot =
    val tournament = tournamentRepository
      .findById(tournamentId)
      .getOrElse(throw NoSuchElementException(s"Tournament ${tournamentId.value} was not found"))
    val stage = requireStage(tournament, stageId)
    val participants = resolveParticipants(tournament, stage).map(_.id)
    val records = stageRecords(tournamentId, stageId)
    val advancement = tournamentRuleEngine.projectAdvancement(
      tournament,
      stage,
      tournamentRuleEngine.buildStageRanking(tournament, stage, participants, records, at),
      at
    )
    tournamentRuleEngine.buildKnockoutProgression(
      tournament = tournament,
      stage = stage,
      advancement = advancement,
      tables = tableRepository.findByTournamentAndStage(tournamentId, stageId),
      records = records,
      at = at
    )

  def materializeUnlockedTables(
      tournamentId: TournamentId,
      stageId: TournamentStageId,
      at: Instant = Instant.now()
  ): Vector[Table] =
    transactionManager.inTransaction {
      val tournament = tournamentRepository
        .findById(tournamentId)
        .getOrElse(throw NoSuchElementException(s"Tournament ${tournamentId.value} was not found"))
      val stage = requireStage(tournament, stageId)
      val existingTables = tableRepository.findByTournamentAndStage(tournamentId, stageId)
      val progression = buildProgression(tournamentId, stageId, at)
      val representedClubByPlayer = representativeClubMap(stage)

      val startingTableNo = existingTables.map(_.tableNo).foldLeft(0)(math.max)
      val tablesToCreate = progression.rounds
        .flatMap(_.matches)
        .filter(matchNode => matchNode.unlocked && matchNode.tableId.isEmpty && !matchNode.completed)
        .zipWithIndex
        .map { case (matchNode, index) =>
          val playerIds = matchNode.slots.flatMap(_.playerId)
          if playerIds.size != 4 then
            throw IllegalArgumentException(
              s"Knockout match ${matchNode.id} is unlocked but does not have four resolved players"
            )

          val seats = SeatWind.all.zip(playerIds).map { case (wind, playerId) =>
            val player = playerRepository
              .findById(playerId)
              .getOrElse(throw NoSuchElementException(s"Player ${playerId.value} was not found"))
            TableSeat(
              seat = wind,
              playerId = playerId,
              clubId = representedClubByPlayer.get(playerId).orElse(player.clubId)
            )
          }

          Table(
            id = IdGenerator.tableId(),
            tableNo = startingTableNo + index + 1,
            tournamentId = tournamentId,
            stageId = stageId,
            seats = seats,
            stageRoundNumber = matchNode.roundNumber
          ).bindKnockoutMatch(
            matchId = matchNode.id,
            roundNumber = matchNode.roundNumber,
            feeders = matchNode.sourceMatchIds
          )
        }

      val savedTables = tablesToCreate.map(tableRepository.save)

      if savedTables.nonEmpty then
        val updatedTournament = tournament
          .activateStage(stageId)
          .updateStage(stageId, _.registerScheduledTables(savedTables.map(_.id)))

        tournamentRepository.save(
          if tournament.status == TournamentStatus.RegistrationOpen then updatedTournament.markScheduled
          else updatedTournament
        )

      savedTables
    }

  def reconcileAfterMatchMutation(
      tournamentId: TournamentId,
      stageId: TournamentStageId,
      mutatedMatchId: String,
      at: Instant = Instant.now()
  ): Vector[Table] =
    transactionManager.inTransaction {
      pruneDependentTables(tournamentId, stageId, mutatedMatchId)
      materializeUnlockedTables(tournamentId, stageId, at)
    }

  private def requireStage(
      tournament: Tournament,
      stageId: TournamentStageId
  ): TournamentStage =
    tournament.stages
      .find(_.id == stageId)
      .getOrElse(throw NoSuchElementException(s"Stage ${stageId.value} was not found"))

  private def stageRecords(
      tournamentId: TournamentId,
      stageId: TournamentStageId
  ): Vector[MatchRecord] =
    matchRecordRepository.findAll()
      .filter(record => record.tournamentId == tournamentId && record.stageId == stageId)

  private def resolveParticipants(
      tournament: Tournament,
      stage: TournamentStage
  ): Vector[Player] =
    val stagePlayerIds = stage.lineupSubmissions.flatMap(_.activePlayerIds).distinct

    val fallbackPlayerIds =
      val registeredClubMembers = tournament.participatingClubs.flatMap { clubId =>
        clubRepository.findById(clubId).toVector.flatMap(_.members)
      }
      val whitelistedPlayers = tournament.whitelist.flatMap(_.playerId)
      val whitelistedClubMembers = tournament.whitelist.flatMap { entry =>
        entry.clubId.toVector.flatMap(clubId => clubRepository.findById(clubId).toVector.flatMap(_.members))
      }

      (tournament.participatingPlayers ++ whitelistedPlayers ++ registeredClubMembers ++ whitelistedClubMembers).distinct

    val targetPlayerIds =
      if stagePlayerIds.nonEmpty then stagePlayerIds else fallbackPlayerIds

    targetPlayerIds.flatMap { playerId =>
      playerRepository.findById(playerId).filter(_.status == PlayerStatus.Active)
    }

  private def pruneDependentTables(
      tournamentId: TournamentId,
      stageId: TournamentStageId,
      sourceMatchId: String
  ): Unit =
    val dependentTables = tableRepository.findByTournamentAndStage(tournamentId, stageId)
      .filter(_.feederMatchIds.contains(sourceMatchId))

    dependentTables.foreach { table =>
      if table.matchRecordId.nonEmpty || table.status != TableStatus.WaitingPreparation then
        throw IllegalArgumentException(
          s"Cannot reflow knockout bracket because dependent table ${table.id.value} has already started"
        )

      table.bracketMatchId.foreach { downstreamMatchId =>
        pruneDependentTables(tournamentId, stageId, downstreamMatchId)
      }

      tableRepository.delete(table.id)
    }

  private def representativeClubMap(stage: TournamentStage): Map[PlayerId, ClubId] =
    val pairings = stage.lineupSubmissions.flatMap { submission =>
      submission.activePlayerIds.map(_ -> submission.clubId)
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
