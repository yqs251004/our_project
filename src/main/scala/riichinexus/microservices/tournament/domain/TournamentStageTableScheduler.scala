package riichinexus.microservices.tournament.domain

import java.sql.Connection
import java.util.NoSuchElementException

import riichinexus.bootstrap.TournamentModuleContext
import riichinexus.domain.model.*
import riichinexus.microservices.tournament.domain.model.*
import riichinexus.microservices.club.domain.model.*
import riichinexus.microservices.club.objects.ClubRelationKind
import riichinexus.microservices.player.objects.*
import riichinexus.microservices.player.tables.player.PlayerTable
import riichinexus.microservices.tournament.objects.{AdvancementRuleType, SeatWind, TableStatus, TournamentFormat, TournamentStatus}

object TournamentStageTableScheduler:
  def schedule(
      connection: Connection,
      module: TournamentModuleContext,
      tournamentId: TournamentId,
      stageId: TournamentStageId
  ): Vector[Table] =
    val tournament = riichinexus.microservices.tournament.tables.tournament.TournamentTable
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

    if isKnockoutStage then
      KnockoutStageCoordinator.materializeUnlockedTables(connection, module.transactionManager, tournamentId, stageId)
      riichinexus.microservices.tournament.tables.tournamentgame.TournamentGameTable.findByTournamentAndStage(connection, tournamentId, stageId).sortBy(table =>
        (table.stageRoundNumber, table.tableNo, table.id.value)
      )
    else
      scheduleNonKnockoutStage(connection, module, tournament, stage)

  private def scheduleNonKnockoutStage(
      connection: Connection,
      module: TournamentModuleContext,
      tournament: Tournament,
      stage: TournamentStage
  ): Vector[Table] =
    val tournamentPlayers = resolveParticipants(connection, module, tournament, stage)
    if tournamentPlayers.size < 4 then
      throw IllegalArgumentException(
        s"Stage ${stage.id.value} needs at least four active players before scheduling"
      )
    if stage.format != TournamentFormat.Custom && tournamentPlayers.size % 4 != 0 then
      throw IllegalArgumentException(
        s"Stage ${stage.id.value} requires player counts divisible by four; got ${tournamentPlayers.size}"
      )

    val existingTables = riichinexus.microservices.tournament.tables.tournamentgame.TournamentGameTable.findByTournamentAndStage(connection, tournament.id, stage.id)
    val preparedTournament =
      prepareNonKnockoutRoundIfNeeded(
        connection = connection,
        module = module,
        tournament = tournament,
        stage = stage,
        participants = tournamentPlayers,
        existingTables = existingTables
      )
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
              id = IdGenerator.tableId(),
              tableNo = plan.tableNo,
              tournamentId = tournament.id,
              stageId = stage.id,
              seats = plan.seats,
              stageRoundNumber = plan.roundNumber
            )
          )
        }

        val updatedTournament = preparedTournament
          .activateStage(stage.id)
          .updateStage(stage.id, _.consumePendingPlans(plansToMaterialize, createdTables.map(_.id)))
        riichinexus.microservices.tournament.tables.tournament.TournamentTable.save(connection, 
          if updatedTournament.status == TournamentStatus.RegistrationOpen then updatedTournament.markScheduled
          else updatedTournament
        )
        createdTables

    if materializedTables.nonEmpty || existingTables.nonEmpty || preparedStage.pendingTablePlans.nonEmpty then
      riichinexus.microservices.tournament.tables.tournamentgame.TournamentGameTable.findByTournamentAndStage(connection, tournament.id, stage.id).sortBy(table =>
        (table.stageRoundNumber, table.tableNo, table.id.value)
      )
    else Vector.empty

  private def resolveParticipants(
      connection: Connection,
      module: TournamentModuleContext,
      tournament: Tournament,
      stage: TournamentStage
  ): Vector[Player] =
    val clubsById = riichinexus.microservices.club.tables.club.ClubTable.findByIds(connection, 
      (tournament.participatingClubs ++ tournament.whitelist.flatMap(_.clubId)).distinct
    ).map(club => club.id -> club).toMap

    val fallbackPlayerIds =
      val registeredClubMembers = tournament.participatingClubs.flatMap { clubId =>
        clubsById.get(clubId).toVector.flatMap(_.members)
      }
      val whitelistedPlayers = tournament.whitelist.flatMap(_.playerId)
      val whitelistedClubMembers = tournament.whitelist.flatMap { entry =>
        entry.clubId.toVector.flatMap(clubId => clubsById.get(clubId).toVector.flatMap(_.members))
      }

      (tournament.participatingPlayers ++ whitelistedPlayers ++ registeredClubMembers ++ whitelistedClubMembers).distinct

    val playersById = PlayerTable
      .findByIds(connection, (stage.lineupSubmissions.flatMap(_.seats.map(_.playerId)) ++ fallbackPlayerIds).distinct)
      .map(player => player.id -> player)
      .toMap
    val stagePlayerIds = StageLineupResolver.resolveEligiblePlayers(stage, playersById.get)

    val targetPlayerIds =
      StageLineupResolver.resolveTargetPlayerIds(tournament, stagePlayerIds, fallbackPlayerIds)

    targetPlayerIds.flatMap { playerId =>
      playersById.get(playerId).filter(_.status == PlayerStatus.Active)
    }

  private def prepareNonKnockoutRoundIfNeeded(
      connection: Connection,
      module: TournamentModuleContext,
      tournament: Tournament,
      stage: TournamentStage,
      participants: Vector[Player],
      existingTables: Vector[Table]
  ): Tournament =
    if stage.pendingTablePlans.nonEmpty then tournament
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
        case None => tournament
        case Some(roundNumber) =>
          val tournamentHistory =
            riichinexus.microservices.tournament.tables.matchrecord.MatchRecordTable.findByTournamentAndStage(connection, tournament.id, stage.id)
          val planningStage =
            if roundNumber == stage.currentRound then stage
            else stage.advanceRound(roundNumber)
          val startingTableNo = existingTables.map(_.tableNo).foldLeft(0)(math.max)
          val plans = plannedTablesForStage(
            connection = connection,
            module = module,
            tournament = tournament,
            stage = planningStage,
            participants = participants,
            history = tournamentHistory,
            roundNumber = roundNumber
          )
            .zipWithIndex
            .map { case (planned, index) =>
              StageTablePlan(
                roundNumber = roundNumber,
                tableNo = startingTableNo + index + 1,
                seats = planned.seats
              )
            }

          val updatedTournament = tournament.updateStage(stage.id, _.queueRoundPlans(roundNumber, plans))
          riichinexus.microservices.tournament.tables.tournament.TournamentTable.save(connection, 
            if updatedTournament.status == TournamentStatus.RegistrationOpen then updatedTournament.markScheduled
            else updatedTournament
          )

  private def plannedTablesForStage(
      connection: Connection,
      module: TournamentModuleContext,
      tournament: Tournament,
      stage: TournamentStage,
      participants: Vector[Player],
      history: Vector[MatchRecord],
      roundNumber: Int
  ): Vector[PlannedTable] =
    val clubRelations = buildClubRelationIndex(
      riichinexus.microservices.club.tables.club.ClubTable.findFiltered(connection, activeOnly = true)
    )
    stage.format match
      case TournamentFormat.RoundRobin =>
        buildRoundRobinTables(participants, stage, roundNumber)
      case TournamentFormat.Custom =>
        val selectedPlayers = selectCustomStageParticipants(module, tournament, stage, participants, history, roundNumber)
        SeatingPolicy.planTables(selectedPlayers, stage, history, clubRelations)
      case _ =>
        SeatingPolicy.planTables(participants, stage, history, clubRelations)

  private def buildClubRelationIndex(
      clubs: Vector[Club]
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

  private def selectCustomStageParticipants(
      module: TournamentModuleContext,
      tournament: Tournament,
      stage: TournamentStage,
      participants: Vector[Player],
      history: Vector[MatchRecord],
      roundNumber: Int
  ): Vector[Player] =
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

  private def customStageTableCount(
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

  private def buildRoundRobinTables(
      participants: Vector[Player],
      stage: TournamentStage,
      roundNumber: Int
  ): Vector[PlannedTable] =
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
      PlannedTable(
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
      players: Vector[Player],
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
