package riichinexus.microservices.tournament.domain.stage.functions.scheduling

import riichinexus.microservices.tournament.domain.stage.functions.lineup.StageLineupResolver
import riichinexus.microservices.tournament.domain.stage.functions.rules.{TournamentRuleEngine, TournamentStageParticipantResolver}
import riichinexus.microservices.tournament.domain.stage.functions.rules.knockout.KnockoutStageCoordinator

import riichinexus.microservices.tournament.domain.competition.functions.TournamentFunctions
import cats.effect.IO
import riichinexus.system.api.ApiPlanContext
import java.sql.Connection
import java.util.NoSuchElementException
import java.time.Instant

import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.microservices.club.objects.clubmanagement.ClubId
import riichinexus.microservices.tournament.domain.identity.functions.TournamentIdGenerator
import riichinexus.microservices.tournament.objects.identity.{TournamentId, TournamentStageId}
import riichinexus.microservices.tournament.domain.stage.functions.TournamentStageFunctions
import riichinexus.microservices.tournament.domain.stage.model.{StageTablePlan, Table, TournamentStage}
import riichinexus.microservices.tournament.domain.matchrecord.model.MatchRecord
import riichinexus.microservices.tournament.domain.competition.model.Tournament
import riichinexus.microservices.club.objects.`private`.ClubPrivateView
import riichinexus.microservices.tournament.objects.stage.table.TableSeat
import riichinexus.microservices.club.objects.relationmanagement.ClubRelationKind
import riichinexus.microservices.player.objects.`private`.PlayerPrivateView
import riichinexus.microservices.tournament.objects.stage.rules.progression.AdvancementRuleType
import riichinexus.microservices.tournament.objects.stage.table.{SeatWind, TableStatus}
import riichinexus.microservices.tournament.objects.competition.{TournamentFormat, TournamentStatus}

/** TournamentStageTableScheduler 负责赛事阶段牌桌调度器 相关的领域编排、构建或投影计算。 */

private[tournament] object TournamentStageTableScheduler:
  def progressAfterTableArchived(
      connection: Connection,
      table: Table,
      at: Instant = Instant.now()
  ): IO[Vector[Table]] =
    schedule(
      connection = connection,
      tournamentId = table.tournamentId,
      stageId = table.stageId,
      at = at
    )

  def schedule(
      connection: Connection,
      tournamentId: TournamentId,
      stageId: TournamentStageId,
      at: Instant = Instant.now()
  ): IO[Vector[Table]] =
    IO.blocking {
      val tournament = riichinexus.microservices.tournament.tables.tournaments.TournamentTable
        .findById(connection, tournamentId)
        .getOrElse(throw IllegalArgumentException(s"Tournament ${tournamentId.value} was not found"))

      val stage = tournament.stages
        .find(_.id == stageId)
        .getOrElse(throw IllegalArgumentException(s"Stage ${stageId.value} was not found"))

      if tournament.status == TournamentStatus.Draft then
        throw IllegalArgumentException(
          s"Tournament ${tournamentId.value} must be published before scheduling tables"
        )

      val isKnockoutStage =
        stage.format == TournamentFormat.Knockout ||
          stage.format == TournamentFormat.Finals ||
          stage.advancementRule.ruleType == AdvancementRuleType.KnockoutElimination

      (tournament, stage, isKnockoutStage)
    }.flatMap { case (tournament, stage, isKnockoutStage) =>
      if isKnockoutStage then
        for
          _ <- KnockoutStageCoordinator.materializeUnlockedTables(connection, tournamentId, stageId, at)
          tables <- IO.blocking {
            ensureScheduledTournamentWithTablesIsActive(connection, tournamentId, stageId)
            riichinexus.microservices.tournament.tables.tournamentgame.TournamentGameTable.findByTournamentAndStage(connection, tournamentId, stageId).sortBy(table =>
              (table.stageRoundNumber, table.tableNo, table.id.value)
            )
          }
        yield tables
      else
        scheduleNonKnockoutStage(connection, tournament, stage)
    }

  private def scheduleNonKnockoutStage(
      connection: Connection,
      tournament: Tournament,
      stage: TournamentStage
  ): IO[Vector[Table]] =
    for
      tournamentPlayers <- TournamentStageParticipantResolver.resolveParticipants(ApiPlanContext(bearerToken = None, connection = connection), tournament, stage)
      tables <- IO.blocking {
        if tournamentPlayers.size < 4 then
          throw IllegalArgumentException(
            s"Stage ${stage.id.value} needs at least four active players before scheduling"
          )
        if stage.format != TournamentFormat.Custom && tournamentPlayers.size % 4 != 0 then
          throw IllegalArgumentException(
            s"Stage ${stage.id.value} requires player counts divisible by four; got ${tournamentPlayers.size}"
          )
        riichinexus.microservices.tournament.tables.tournamentgame.TournamentGameTable.findByTournamentAndStage(connection, tournament.id, stage.id)
      }
      preparedTournament <- prepareNonKnockoutRoundIfNeeded(
        connection = connection,
        tournament = tournament,
        stage = stage,
        participants = tournamentPlayers,
        existingTables = tables
      )
      scheduledTables <- IO.blocking {
        val preparedStage = requireStage(preparedTournament, stage.id)
        val refreshedTables = riichinexus.microservices.tournament.tables.tournamentgame.TournamentGameTable.findByTournamentAndStage(connection, tournament.id, stage.id)
        val activePoolUsage = refreshedTables.count(_.status != TableStatus.Archived)
        val availablePoolSlots = math.max(0, preparedStage.schedulingPoolSize - activePoolUsage)

        val materializedTables =
          if availablePoolSlots <= 0 || preparedStage.pendingTablePlans.isEmpty then Vector.empty
          else
            val plansToMaterialize = preparedStage.pendingTablePlans.take(availablePoolSlots)
            val createdTables = plansToMaterialize.map { plan =>
              riichinexus.microservices.tournament.tables.tournamentgame.TournamentGameTable.save(connection,
                Table(
                  id = TournamentIdGenerator.tableId(),
                  tableNo = plan.tableNo,
                  tournamentId = tournament.id,
                  stageId = stage.id,
                  seats = plan.seats,
                  stageRoundNumber = plan.roundNumber
                )
              )
            }

            val activatedTournament = TournamentFunctions.activateStage(preparedTournament, stage.id)
            val updatedTournament = TournamentFunctions.updateStage(
              activatedTournament,
              stage.id,
              currentStage => TournamentStageFunctions.consumePendingPlans(currentStage, plansToMaterialize, createdTables.map(_.id))
            )
            riichinexus.microservices.tournament.tables.tournaments.TournamentTable.save(connection,
              if updatedTournament.status == TournamentStatus.RegistrationOpen then TournamentFunctions.markScheduled(updatedTournament)
              else updatedTournament
            )
            createdTables

        if materializedTables.isEmpty && tables.nonEmpty then
          ensureScheduledTournamentWithTablesIsActive(connection, tournament.id, stage.id)

        if materializedTables.nonEmpty || tables.nonEmpty || preparedStage.pendingTablePlans.nonEmpty then
          riichinexus.microservices.tournament.tables.tournamentgame.TournamentGameTable.findByTournamentAndStage(connection, tournament.id, stage.id).sortBy(table =>
            (table.stageRoundNumber, table.tableNo, table.id.value)
          )
        else Vector.empty
      }
    yield scheduledTables

  private def ensureScheduledTournamentWithTablesIsActive(
      connection: Connection,
      tournamentId: TournamentId,
      stageId: TournamentStageId
  ): Unit =
    val stageTables =
      riichinexus.microservices.tournament.tables.tournamentgame.TournamentGameTable.findByTournamentAndStage(connection, tournamentId, stageId)
    if stageTables.nonEmpty then
      riichinexus.microservices.tournament.tables.tournaments.TournamentTable.findById(connection, tournamentId).foreach { latestTournament =>
        if latestTournament.status == TournamentStatus.Scheduled then
          riichinexus.microservices.tournament.tables.tournaments.TournamentTable.save(
            connection,
            TournamentFunctions.activateStage(latestTournament, stageId)
          )
      }

  private def prepareNonKnockoutRoundIfNeeded(
      connection: Connection,
      tournament: Tournament,
      stage: TournamentStage,
      participants: Vector[PlayerPrivateView],
      existingTables: Vector[Table]
  ): IO[Tournament] =
    if stage.pendingTablePlans.nonEmpty then IO.pure(tournament)
    else
      val effectiveRoundLimit = StageLineupResolver.effectiveRoundLimit(stage)
      val tablesPerRound = participants.size / 4
      val currentRoundTables = existingTables.filter(_.stageRoundNumber == stage.currentRound)
      val initialRound = existingTables.isEmpty && stage.currentRound == 1
      val currentRoundFullyArchived =
        currentRoundTables.nonEmpty &&
          currentRoundTables.size >= tablesPerRound &&
          currentRoundTables.forall(_.status == TableStatus.Archived)

      val targetRound =
        if initialRound then Some(1)
        else if currentRoundFullyArchived && stage.currentRound < effectiveRoundLimit then Some(stage.currentRound + 1)
        else None

      targetRound match
        case None => IO.pure(tournament)
        case Some(roundNumber) =>
          for
            planningInput <- IO.blocking {
              val tournamentHistory =
                riichinexus.microservices.tournament.tables.matchrecord.MatchRecordTable.findByTournamentAndStage(connection, tournament.id, stage.id)
              val planningStage =
                if roundNumber == stage.currentRound then stage
                else TournamentStageFunctions.advanceRound(stage, roundNumber)
              val startingTableNo = existingTables.map(_.tableNo).foldLeft(0)(math.max)
              (tournamentHistory, planningStage, startingTableNo)
            }
            (tournamentHistory, planningStage, startingTableNo) = planningInput
            plans <- plannedTablesForStage(
              connection = connection,
              tournament = tournament,
              stage = planningStage,
              participants = participants,
              history = tournamentHistory,
              roundNumber = roundNumber
            )
            savedTournament <- IO.blocking {
              val numberedPlans = plans
                .zipWithIndex
                .map { case (planned, index) =>
                  planned.copy(tableNo = startingTableNo + index + 1)
                }

              val updatedTournament = TournamentFunctions.updateStage(
                tournament,
                stage.id,
                currentStage => TournamentStageFunctions.queueRoundPlans(currentStage, roundNumber, numberedPlans)
              )
              riichinexus.microservices.tournament.tables.tournaments.TournamentTable.save(connection,
                if updatedTournament.status == TournamentStatus.RegistrationOpen then TournamentFunctions.markScheduled(updatedTournament)
                else updatedTournament
              )
            }
          yield savedTournament

  private def plannedTablesForStage(
      connection: Connection,
      tournament: Tournament,
      stage: TournamentStage,
      participants: Vector[PlayerPrivateView],
      history: Vector[MatchRecord],
      roundNumber: Int
  ): IO[Vector[StageTablePlan]] =
    TournamentStageParticipantResolver.resolveClubRelationIndex(ApiPlanContext(bearerToken = None, connection = connection)).map { clubRelations =>
      stage.format match
        case TournamentFormat.RoundRobin =>
          buildRoundRobinTables(participants, stage, roundNumber)
        case TournamentFormat.Custom =>
          val selectedPlayers = selectCustomStageParticipants(tournament, stage, participants, history, roundNumber)
          SeatingPolicy.planTables(selectedPlayers, stage, roundNumber, history, clubRelations)
        case _ =>
          SeatingPolicy.planTables(participants, stage, roundNumber, history, clubRelations)
    }

  private def buildClubRelationIndex(
      clubs: Vector[ClubPrivateView]
  ): Map[(ClubId, ClubId), ClubRelationKind] =
    clubs.flatMap { club =>
      club.relations.collect {
        case relation if relation.relation != ClubRelationKind.Neutral && relation.targetClubId != club.id =>
          val pair =
            if club.id.value <= relation.targetClubId.value then (club.id, relation.targetClubId)
            else (relation.targetClubId, club.id)
          pair -> relation.relation
      }
    }
      .groupBy(_._1)
      .view
      .mapValues(_.map(_._2).minBy {
        case ClubRelationKind.Alliance => 0
        case ClubRelationKind.Rivalry  => 1
        case ClubRelationKind.Neutral  => 2
      })
      .toMap

  private[scheduling] def selectCustomStageParticipants(
      tournament: Tournament,
      stage: TournamentStage,
      participants: Vector[PlayerPrivateView],
      history: Vector[MatchRecord],
      roundNumber: Int
  ): Vector[PlayerPrivateView] =
    val maxTables = math.max(1, math.min(participants.size / 4, customStageTableCount(stage, participants.size)))
    val targetParticipants = maxTables * 4
    val rankingOrder =
      if history.nonEmpty then
        val ranking = TournamentRuleEngine.buildStageRanking(
          tournament,
          stage,
          participants.map(_.id),
          history,
          java.time.Instant.now()
        )
        ranking.entries.flatMap(entry => participants.find(_.id == entry.playerId))
      else Vector.empty

    val seededOrder =
      if rankingOrder.nonEmpty then rankingOrder
      else
        participants.sortBy(player => (-player.elo, player.nickname, player.id.value))

    val rotatedOrder =
      if seededOrder.isEmpty then seededOrder
      else rotateVector(seededOrder, (roundNumber - 1) % seededOrder.size)

    rotatedOrder.take(targetParticipants)

  private[scheduling] def customStageTableCount(
      stage: TournamentStage,
      participantCount: Int
  ): Int =
    val availableTables = participantCount / 4
    require(availableTables >= 1, s"Stage ${stage.id.value} needs at least one full table")
    stage.advancementRule.targetTableCount match
      case Some(value) =>
        require(value >= 1, s"Stage ${stage.id.value} targetTableCount must be positive")
        require(value <= availableTables, s"Stage ${stage.id.value} targetTableCount exceeds available tables")
        value
      case None =>
        availableTables

  private[scheduling] def buildRoundRobinTables(
      participants: Vector[PlayerPrivateView],
      stage: TournamentStage,
      roundNumber: Int
  ): Vector[StageTablePlan] =
    require(participants.size % 4 == 0, s"Stage ${stage.id.value} requires full four-player round robin pods")
    val seededPlayers = participants.sortBy(player => (-player.elo, player.nickname, player.id.value))
    val tableCount = participants.size / 4
    val rows = seededPlayers.grouped(tableCount).toVector
    val representedClubByPlayer = representedClubMap(stage)
    val preferredWindByPlayer = preferredWindMap(stage)

    val rotatedRows = rows.zipWithIndex.map { case (row, rowIndex) =>
      if row.isEmpty then row
      else rotateVector(row, ((roundNumber - 1) * rowIndex) % row.size)
    }

    (0 until tableCount).toVector.map { tableIndex =>
      val group = rotatedRows.map(_(tableIndex))
      StageTablePlan(
        roundNumber = roundNumber,
        tableNo = tableIndex + 1,
        seats =
          assignSeatsForGroup(
            group,
            representedClubByPlayer,
            preferredWindByPlayer,
            roundNumber + tableIndex
          )
      )
    }

  private def representedClubMap(stage: TournamentStage): Map[PlayerId, ClubId] =
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

  private def assignSeatsForGroup(
      players: Vector[PlayerPrivateView],
      representedClubByPlayer: Map[PlayerId, ClubId],
      preferredWindByPlayer: Map[PlayerId, SeatWind],
      shift: Int
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
      }

    SeatWind.all.zip(rotateVector(chosenPlayers, shift % players.size)).map { case (seat, player) =>
      TableSeat(
        seat = seat,
        playerId = player.id,
        clubId = representedClubByPlayer.get(player.id).orElse(player.clubId)
      )
    }

  private def rotateVector[A](values: Vector[A], shift: Int): Vector[A] =
    if values.isEmpty then values
    else
      val normalized = math.floorMod(shift, values.size)
      values.drop(normalized) ++ values.take(normalized)

  private def requireStage(
      tournament: Tournament,
      stageId: TournamentStageId
  ): TournamentStage =
    tournament.stages
      .find(_.id == stageId)
      .getOrElse(throw NoSuchElementException(s"Stage ${stageId.value} was not found"))
