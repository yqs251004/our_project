package riichinexus.microservices.tournament.domain.tablemanagement.functions
import riichinexus.microservices.player.api.`private`.ResolvePlayersPrivateAPIMessage

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
import cats.effect.unsafe.implicits.global
import riichinexus.system.api.ApiPlanContext
import java.sql.Connection
import java.util.NoSuchElementException
import java.time.Instant

import riichinexus.microservices.player.domain.functions.PlayerIdGenerator
import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.microservices.club.domain.functions.ClubIdGenerator
import riichinexus.microservices.club.objects.clubmanagement.ClubId
import riichinexus.microservices.club.objects.membershipmanagement.MembershipApplicationId
import riichinexus.microservices.tournament.domain.functions.TournamentIdGenerator
import riichinexus.microservices.tournament.objects.lineupmanagement.LineupSubmissionId
import riichinexus.microservices.tournament.objects.paifumanagement.PaifuId
import riichinexus.microservices.tournament.objects.recordmanagement.MatchRecordId
import riichinexus.microservices.tournament.objects.settlementmanagement.SettlementSnapshotId
import riichinexus.microservices.tournament.objects.tablemanagement.TableId
import riichinexus.microservices.tournament.objects.tournamentmanagement.{TournamentId, TournamentStageId}
import riichinexus.microservices.tournament.appeal.domain.functions.AppealIdGenerator
import riichinexus.microservices.tournament.appeal.objects.ticketmanagement.AppealTicketId
import riichinexus.microservices.auth.domain.functions.AuthIdGenerator
import riichinexus.microservices.auth.objects.sessionmanagement.GuestSessionId
import riichinexus.microservices.audit.domain.functions.AuditIdGenerator
import riichinexus.microservices.audit.domain.auditevent.AuditEventId
import riichinexus.microservices.opsanalytics.domain.functions.OpsAnalyticsIdGenerator
import riichinexus.microservices.opsanalytics.objects.advancedstats.AdvancedStatsRecomputeTaskId
import riichinexus.microservices.tournament.domain.tournamentmanagement.functions.{TournamentFunctions, TournamentStageFunctions}
import riichinexus.microservices.tournament.domain.lineupmanagement.model.*
import riichinexus.microservices.tournament.domain.recordmanagement.model.*
import riichinexus.microservices.tournament.domain.settlementmanagement.model.*
import riichinexus.microservices.tournament.domain.tablemanagement.model.*
import riichinexus.microservices.tournament.domain.tournamentmanagement.model.*
import riichinexus.microservices.club.domain.Club
import riichinexus.microservices.club.domain.clubmanagement.model.*
import riichinexus.microservices.club.domain.membershipmanagement.model.*
import riichinexus.microservices.club.domain.rankprivilegemanagement.model.*
import riichinexus.microservices.club.domain.relationmanagement.model.*
import riichinexus.microservices.tournament.objects.tablemanagement.TableSeat
import riichinexus.microservices.club.api.`private`.{ListClubsPrivateAPIMessage, ResolveClubPrivateAPIMessage, ResolveClubsPrivateAPIMessage, SaveClubPrivateAPIMessage}
import riichinexus.microservices.club.objects.relationmanagement.ClubRelationKind
import riichinexus.microservices.player.domain.Player
import riichinexus.microservices.player.objects.*
import riichinexus.microservices.player.api.{CreatePlayerAPIMessage, GetPlayerAPIMessage, ListPlayersAPIMessage}
import riichinexus.microservices.tournament.objects.rulesmanagement.stageprogression.AdvancementRuleType
import riichinexus.microservices.tournament.objects.tablemanagement.{SeatWind, TableStatus}
import riichinexus.microservices.tournament.objects.tournamentmanagement.{TournamentFormat, TournamentStatus}

object TournamentStageTableScheduler:
  def progressAfterTableArchived(
      connection: Connection,
      table: Table,
      at: Instant = Instant.now()
  ): Vector[Table] =
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
  ): Vector[Table] =
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

    if isKnockoutStage then
      KnockoutStageCoordinator.materializeUnlockedTables(connection, tournamentId, stageId, at)
      ensureScheduledTournamentWithTablesIsActive(connection, tournamentId, stageId)
      riichinexus.microservices.tournament.tables.tournamentgame.TournamentGameTable.findByTournamentAndStage(connection, tournamentId, stageId).sortBy(table =>
        (table.stageRoundNumber, table.tableNo, table.id.value)
      )
    else
      scheduleNonKnockoutStage(connection, tournament, stage)

  private def scheduleNonKnockoutStage(
      connection: Connection,
      tournament: Tournament,
      stage: TournamentStage
  ): Vector[Table] =
    val tournamentPlayers = resolveParticipants(connection, tournament, stage)
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

    if materializedTables.isEmpty && existingTables.nonEmpty then
      ensureScheduledTournamentWithTablesIsActive(connection, tournament.id, stage.id)

    if materializedTables.nonEmpty || existingTables.nonEmpty || preparedStage.pendingTablePlans.nonEmpty then
      riichinexus.microservices.tournament.tables.tournamentgame.TournamentGameTable.findByTournamentAndStage(connection, tournament.id, stage.id).sortBy(table =>
        (table.stageRoundNumber, table.tableNo, table.id.value)
      )
    else Vector.empty

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

  private def resolveParticipants(
      connection: Connection,
      tournament: Tournament,
      stage: TournamentStage
  ): Vector[Player] =
    val clubIds = (tournament.participatingClubs ++ tournament.whitelist.flatMap(_.clubId)).distinct
    val clubsById = ResolveClubsPrivateAPIMessage(clubIds)
      .plan(ApiPlanContext(bearerToken = None, connection = connection))
      .unsafeRunSync()
      .map(club => club.id -> club)
      .toMap

    val fallbackPlayerIds =
      val registeredClubMembers = tournament.participatingClubs.flatMap { clubId =>
        clubsById.get(clubId).toVector.flatMap(_.members)
      }
      val whitelistedPlayers = tournament.whitelist.flatMap(_.playerId)
      val whitelistedClubMembers = tournament.whitelist.flatMap { entry =>
        entry.clubId.toVector.flatMap(clubId => clubsById.get(clubId).toVector.flatMap(_.members))
      }

      (tournament.participatingPlayers ++ whitelistedPlayers ++ registeredClubMembers ++ whitelistedClubMembers).distinct

    val playersById = ResolvePlayersPrivateAPIMessage((stage.lineupSubmissions.flatMap(_.seats.map(_.playerId)) ++ fallbackPlayerIds).distinct)
      .plan(ApiPlanContext(bearerToken = None, connection = connection))
      .unsafeRunSync()
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
            else TournamentStageFunctions.advanceRound(stage, roundNumber)
          val startingTableNo = existingTables.map(_.tableNo).foldLeft(0)(math.max)
          val plans = plannedTablesForStage(
            connection = connection,
            tournament = tournament,
            stage = planningStage,
            participants = participants,
            history = tournamentHistory,
            roundNumber = roundNumber
          )
            .zipWithIndex
            .map { case (planned, index) =>
              planned.copy(tableNo = startingTableNo + index + 1)
            }

          val updatedTournament = TournamentFunctions.updateStage(
            tournament,
            stage.id,
            currentStage => TournamentStageFunctions.queueRoundPlans(currentStage, roundNumber, plans)
          )
          riichinexus.microservices.tournament.tables.tournaments.TournamentTable.save(connection, 
            if updatedTournament.status == TournamentStatus.RegistrationOpen then TournamentFunctions.markScheduled(updatedTournament)
            else updatedTournament
          )

  private def plannedTablesForStage(
      connection: Connection,
      tournament: Tournament,
      stage: TournamentStage,
      participants: Vector[Player],
      history: Vector[MatchRecord],
      roundNumber: Int
  ): Vector[StageTablePlan] =
    val clubRelations = buildClubRelationIndex(
      ListClubsPrivateAPIMessage(activeOnly = true).plan(ApiPlanContext(bearerToken = None, connection = connection)).unsafeRunSync()
    )
    stage.format match
      case TournamentFormat.RoundRobin =>
        buildRoundRobinTables(participants, stage, roundNumber)
      case TournamentFormat.Custom =>
        val selectedPlayers = selectCustomStageParticipants(tournament, stage, participants, history, roundNumber)
        SeatingPolicy.planTables(selectedPlayers, stage, roundNumber, history, clubRelations)
      case _ =>
        SeatingPolicy.planTables(participants, stage, roundNumber, history, clubRelations)

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

  private[tablemanagement] def selectCustomStageParticipants(
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

  private[tablemanagement] def customStageTableCount(
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

  private[tablemanagement] def buildRoundRobinTables(
      participants: Vector[Player],
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
