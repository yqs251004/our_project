package riichinexus.microservices.tournament.domain.stage.functions.rules.knockout

import riichinexus.microservices.tournament.domain.stage.functions.lineup.StageLineupResolver
import riichinexus.microservices.tournament.domain.stage.functions.rules.{TournamentRuleEngine, TournamentStageParticipantResolver}

import riichinexus.microservices.tournament.domain.stage.functions.scheduling.TableFunctions
import riichinexus.microservices.tournament.domain.competition.functions.TournamentFunctions
import java.sql.Connection
import java.time.Instant
import java.util.NoSuchElementException

import cats.effect.IO
import riichinexus.system.api.ApiPlanContext
import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.microservices.club.objects.clubmanagement.ClubId
import riichinexus.microservices.tournament.domain.identity.functions.TournamentIdGenerator
import riichinexus.microservices.tournament.objects.tournamentmanagement.{TournamentId, TournamentStageId}
import riichinexus.microservices.tournament.domain.stage.functions.TournamentStageFunctions
import riichinexus.microservices.tournament.domain.stage.model.{Table, TournamentStage}
import riichinexus.microservices.tournament.domain.matchrecord.model.MatchRecord
import riichinexus.microservices.tournament.domain.competition.model.Tournament
import riichinexus.microservices.player.objects.`private`.PlayerPrivateView
import riichinexus.microservices.tournament.objects.rulesmanagement.knockout.KnockoutBracketSnapshot
import riichinexus.microservices.tournament.objects.tablemanagement.{SeatWind, TableSeat, TableStatus}
import riichinexus.microservices.tournament.objects.tournamentmanagement.TournamentStatus
import riichinexus.microservices.tournament.tables.matchrecord.MatchRecordTable
import riichinexus.microservices.tournament.tables.tournaments.TournamentTable
import riichinexus.microservices.tournament.tables.tournamentgame.TournamentGameTable

/** KnockoutStageCoordinator 负责Knockout阶段协调器 相关的领域编排、构建或投影计算。 */

private[tournament] object KnockoutStageCoordinator:
  def buildProgression(
      connection: Connection,
      tournamentId: TournamentId,
      stageId: TournamentStageId,
      at: Instant = Instant.now()
  ): IO[KnockoutBracketSnapshot] =
    for
      base <- IO.blocking {
        val tournament = TournamentTable
          .findById(connection, tournamentId)
          .getOrElse(throw NoSuchElementException(s"Tournament ${tournamentId.value} was not found"))
        val stage = requireStage(tournament, stageId)
        (tournament, stage, stageRecords(connection, tournamentId, stageId), TournamentGameTable.findByTournamentAndStage(connection, tournamentId, stageId))
      }
      (tournament, stage, records, tables) = base
      participants <- TournamentStageParticipantResolver.resolveParticipants(ApiPlanContext(bearerToken = None, connection = connection), tournament, stage)
    yield buildProgression(
      tournament = tournament,
      stage = stage,
      participants = participants,
      records = records,
      tables = tables,
      at = at
    )

  def buildProgression(
      tournament: Tournament,
      stage: TournamentStage,
      participants: Vector[PlayerPrivateView],
      records: Vector[MatchRecord],
      tables: Vector[Table],
      at: Instant
  ): KnockoutBracketSnapshot =
    val ranking = TournamentRuleEngine.buildStageRanking(
      tournament,
      stage,
      participants.map(_.id),
      records,
      at
    )
    val advancement = TournamentRuleEngine.projectAdvancement(
      tournament,
      stage,
      ranking,
      at
    )
    TournamentRuleEngine.buildKnockoutProgression(
      tournament = tournament,
      stage = stage,
      advancement = advancement,
      participants = participants,
      tables = tables,
      records = records,
      at = at
    )

  def materializeUnlockedTables(
      connection: Connection,
      tournamentId: TournamentId,
      stageId: TournamentStageId,
      at: Instant = Instant.now()
  ): IO[Vector[Table]] =
    for
      base <- IO.blocking {
        val tournament = TournamentTable
          .findById(connection, tournamentId)
          .getOrElse(throw NoSuchElementException(s"Tournament ${tournamentId.value} was not found"))
        val stage = requireStage(tournament, stageId)
        (tournament, stage, TournamentGameTable.findByTournamentAndStage(connection, tournamentId, stageId), stageRecords(connection, tournamentId, stageId))
      }
      (tournament, stage, existingTables, records) = base
      participants <- TournamentStageParticipantResolver.resolveParticipants(ApiPlanContext(bearerToken = None, connection = connection), tournament, stage)
      savedTables <- IO.blocking {
      val participantsById = participants.map(player => player.id -> player).toMap
      val progression = buildProgression(tournament, stage, participants, records, existingTables, at)
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
            val player = participantsById.getOrElse(
              playerId,
              throw NoSuchElementException(s"Player ${playerId.value} was not found")
            )
            TableSeat(
              seat = wind,
              playerId = playerId,
              clubId = representedClubByPlayer.get(playerId).orElse(player.clubId)
            )
          }

          TableFunctions.bindKnockoutMatch(
            Table(
              id = TournamentIdGenerator.tableId(),
              tableNo = startingTableNo + index + 1,
              tournamentId = tournamentId,
              stageId = stageId,
              seats = seats,
              stageRoundNumber = matchNode.roundNumber
            ),
            matchId = matchNode.id,
            roundNumber = matchNode.roundNumber,
            feeders = matchNode.sourceMatchIds
          )
        }

      val savedTables = tablesToCreate.map(TournamentGameTable.save(connection, _))

      if savedTables.nonEmpty then
        val activatedTournament = TournamentFunctions.activateStage(tournament, stageId)
        val updatedTournament = TournamentFunctions.updateStage(
          activatedTournament,
          stageId,
          stage => TournamentStageFunctions.registerScheduledTables(stage, savedTables.map(_.id))
        )

        TournamentTable.save(
          connection,
          if tournament.status == TournamentStatus.RegistrationOpen then TournamentFunctions.markScheduled(updatedTournament)
          else updatedTournament
        )

      savedTables
      }
    yield savedTables

  def reconcileAfterMatchMutation(
      connection: Connection,
      tournamentId: TournamentId,
      stageId: TournamentStageId,
      mutatedMatchId: String,
      at: Instant = Instant.now()
  ): IO[Vector[Table]] =
    for
      _ <- IO.blocking(pruneDependentTables(connection, tournamentId, stageId, mutatedMatchId))
      tables <- materializeUnlockedTables(connection, tournamentId, stageId, at)
    yield tables

  private def requireStage(
      tournament: Tournament,
      stageId: TournamentStageId
  ): TournamentStage =
    tournament.stages
      .find(_.id == stageId)
      .getOrElse(throw NoSuchElementException(s"Stage ${stageId.value} was not found"))

  private def stageRecords(
      connection: Connection,
      tournamentId: TournamentId,
      stageId: TournamentStageId
  ): Vector[MatchRecord] =
    MatchRecordTable.findByTournamentAndStage(connection, tournamentId, stageId)

  private def pruneDependentTables(
      connection: Connection,
      tournamentId: TournamentId,
      stageId: TournamentStageId,
      sourceMatchId: String
  ): Unit =
    val dependentTables = TournamentGameTable.findByTournamentAndStage(connection, tournamentId, stageId)
      .filter(_.feederMatchIds.contains(sourceMatchId))

    dependentTables.foreach { table =>
      if table.matchRecordId.nonEmpty || table.status != TableStatus.WaitingPreparation then
        throw IllegalArgumentException(
          s"Cannot reflow knockout bracket because dependent table ${table.id.value} has already started"
        )

      table.bracketMatchId.foreach { downstreamMatchId =>
        pruneDependentTables(connection, tournamentId, stageId, downstreamMatchId)
      }

      TournamentGameTable.delete(connection, table.id)
    }

  private def representativeClubMap(stage: TournamentStage): Map[PlayerId, ClubId] =
    val pairings = StageLineupResolver.submittedPlayersWithClub(stage)
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
