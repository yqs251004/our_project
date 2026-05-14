package riichinexus.microservices.tournament.api

import java.time.Instant
import java.util.NoSuchElementException

import riichinexus.application.ports.*
import riichinexus.domain.event.*
import riichinexus.domain.model.*
import riichinexus.domain.service.*
import riichinexus.microservices.dictionary.api.RuntimeDictionarySupport

private[tournament] trait TournamentWorkflowSupport:
  protected def tournamentRepository: TournamentRepository
  protected def playerRepository: PlayerRepository
  protected def clubRepository: ClubRepository
  protected def globalDictionaryRepository: GlobalDictionaryRepository
  protected def tableRepository: TableRepository
  protected def matchRecordRepository: MatchRecordRepository
  protected def tournamentSettlementRepository: TournamentSettlementRepository
  protected def auditEventRepository: AuditEventRepository
  protected def seatingPolicy: SeatingPolicy
  protected def tournamentRuleEngine: TournamentRuleEngine
  protected def knockoutStageCoordinator: KnockoutStageCoordinator
  protected def stageQueries: TournamentStageQueryService
  protected def eventBus: DomainEventBus
  protected def transactionManager: TransactionManager
  protected def authorizationService: AuthorizationService

  protected def requireClubLineupCapability(
      actor: AccessPrincipal,
      club: Club
  ): Unit =
    val hasBasePermission =
      authorizationService.can(
        actor,
        Permission.SubmitTournamentLineup,
        clubId = Some(club.id)
      )
    val hasDelegatedPrivilege = actor.playerId.exists { playerId =>
      club.members.contains(playerId) && club.hasPrivilege(playerId, ClubPrivilege.PriorityLineup)
    }

    if !hasBasePermission && !hasDelegatedPrivilege then
      throw AuthorizationFailure(
        s"${actor.displayName} is not allowed to perform ${Permission.SubmitTournamentLineup} for club ${club.id.value}"
      )

  protected def resolveParticipants(
      tournament: Tournament,
      stage: TournamentStage
  ): Vector[Player] =
    val clubsById = clubRepository.findByIds(
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

    val playersById = playerRepository.findByIds(
      (stage.lineupSubmissions.flatMap(_.seats.map(_.playerId)) ++ fallbackPlayerIds).distinct
    ).map(player => player.id -> player).toMap
    val stagePlayerIds = StageLineupSupport.resolveEligiblePlayers(stage, playersById.get)

    val targetPlayerIds =
      if stagePlayerIds.nonEmpty then stagePlayerIds else fallbackPlayerIds

    targetPlayerIds.flatMap { playerId =>
      playersById.get(playerId).filter(_.status == PlayerStatus.Active)
    }

  protected final case class StageComputationContext(
      tournament: Tournament,
      stage: TournamentStage,
      participants: Vector[Player],
      records: Vector[MatchRecord]
  )

  protected def stageComputationContext(
      tournamentId: TournamentId,
      stageId: TournamentStageId,
      tournamentHint: Option[Tournament] = None,
      stageHint: Option[TournamentStage] = None
  ): StageComputationContext =
    val tournament = tournamentHint.getOrElse(
      tournamentRepository
        .findById(tournamentId)
        .getOrElse(throw NoSuchElementException(s"Tournament ${tournamentId.value} was not found"))
    )
    val stage = stageHint.getOrElse(requireStage(tournament, stageId))
    StageComputationContext(
      tournament = tournament,
      stage = stage,
      participants = resolveParticipants(tournament, stage),
      records = stageRecords(tournamentId, stageId)
    )

  protected def prepareNonKnockoutRoundIfNeeded(
      tournament: Tournament,
      stage: TournamentStage,
      participants: Vector[Player],
      existingTables: Vector[Table]
  ): Tournament =
    if stage.pendingTablePlans.nonEmpty then tournament
    else
      val effectiveRoundLimit = StageLineupSupport.effectiveRoundLimit(stage)
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
            matchRecordRepository.findByTournamentAndStage(tournament.id, stage.id)
          val planningStage =
            if roundNumber == stage.currentRound then stage
            else stage.advanceRound(roundNumber)
          val startingTableNo = existingTables.map(_.tableNo).foldLeft(0)(math.max)
          val plans = plannedTablesForStage(
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
          tournamentRepository.save(
            if updatedTournament.status == TournamentStatus.RegistrationOpen then updatedTournament.markScheduled
            else updatedTournament
          )

  protected def plannedTablesForStage(
      tournament: Tournament,
      stage: TournamentStage,
      participants: Vector[Player],
      history: Vector[MatchRecord],
      roundNumber: Int
  ): Vector[PlannedTable] =
    val clubRelations = buildClubRelationIndex(clubRepository.findActive())
    stage.format match
      case StageFormat.RoundRobin =>
        buildRoundRobinTables(participants, stage, roundNumber)
      case StageFormat.Custom =>
        val selectedPlayers = selectCustomStageParticipants(tournament, stage, participants, history, roundNumber)
        seatingPolicy.assignTables(selectedPlayers, stage, history, clubRelations)
      case _ =>
        seatingPolicy.assignTables(participants, stage, history, clubRelations)

  protected def expectedTablesPerRound(
      tournament: Tournament,
      stage: TournamentStage,
      participants: Vector[Player],
      records: Vector[MatchRecord],
      at: Instant
  ): Int =
    stage.format match
      case StageFormat.Custom =>
        val selectedPlayers = selectCustomStageParticipants(
          tournament = tournament,
          stage = stage,
          participants = participants,
          history = records,
          roundNumber = math.max(1, stage.currentRound)
        )
        selectedPlayers.size / 4
      case _ =>
        participants.size / 4

  protected def buildClubRelationIndex(
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
      .mapValues(_.map(_._2).sortBy {
        case ClubRelationKind.Alliance => 0
        case ClubRelationKind.Rivalry  => 1
        case ClubRelationKind.Neutral  => 2
      }.head)
      .toMap

  protected def selectCustomStageParticipants(
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
        val ranking = tournamentRuleEngine.buildStageRanking(
          tournament,
          stage,
          participants.map(_.id),
          history,
          Instant.now()
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

  protected def customStageTableCount(
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

  protected def buildRoundRobinTables(
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

  protected def representedClubMap(stage: TournamentStage): Map[PlayerId, ClubId] =
    val pairings = StageLineupSupport.submittedPlayersWithClub(stage)
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

  protected def preferredWindMap(stage: TournamentStage): Map[PlayerId, SeatWind] =
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

  protected def assignSeatsForGroup(
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
      }.toVector

    SeatWind.all.zip(rotateVector(chosenPlayers, shift % players.size)).map { case (seat, player) =>
      TableSeat(
        seat = seat,
        playerId = player.id,
        clubId = representedClubByPlayer.get(player.id).orElse(player.clubId)
      )
    }

  protected def rotateVector[A](values: Vector[A], shift: Int): Vector[A] =
    if values.isEmpty then values
    else
      val normalized = math.floorMod(shift, values.size)
      values.drop(normalized) ++ values.take(normalized)

  protected def normalizeStage(stage: TournamentStage): TournamentStage =
    normalizeStage(stage, RuntimeDictionarySupport.snapshot(globalDictionaryRepository))

  protected def normalizeStage(
      stage: TournamentStage,
      dictionarySnapshot: RuntimeDictionarySupport.DictionarySnapshot
  ): TournamentStage =
    val templatedStage =
      RuntimeDictionarySupport.resolveStageRules(stage, dictionarySnapshot)

    if templatedStage.advancementRule.ruleType == AdvancementRuleType.Custom &&
        templatedStage.advancementRule.note.contains("unconfigured") &&
        templatedStage.advancementRule.templateKey.isEmpty
    then templatedStage.copy(advancementRule = AdvancementRule.defaultFor(templatedStage.format))
    else templatedStage

  protected def stageRecords(
      tournamentId: TournamentId,
      stageId: TournamentStageId
  ): Vector[MatchRecord] =
    matchRecordRepository.findByTournamentAndStage(tournamentId, stageId)

  protected def requireUniqueStageConfiguration(stages: Vector[TournamentStage]): Unit =
    if stages.map(_.id).distinct.size != stages.size then
      throw IllegalArgumentException("Tournament stages must have unique ids")
    if stages.map(_.order).distinct.size != stages.size then
      throw IllegalArgumentException("Tournament stages must have unique ordering")

  protected def requireStage(
      tournament: Tournament,
      stageId: TournamentStageId
  ): TournamentStage =
    tournament.stages
      .find(_.id == stageId)
      .getOrElse(throw NoSuchElementException(s"Stage ${stageId.value} was not found"))

  protected def ensureClubActive(club: Club): Unit =
    if club.dissolvedAt.nonEmpty then
      throw IllegalArgumentException(s"Club ${club.id.value} has already been dissolved")

  protected def requireActivePlayer(player: Player, context: String): Unit =
    if player.status != PlayerStatus.Active then
      throw IllegalArgumentException(context)

  protected def allocatePrizePool(
      prizePool: Long,
      payoutRatios: Vector[Double],
      participantCount: Int
  ): Vector[Long] =
    if prizePool <= 0L || participantCount <= 0 then Vector.fill(participantCount)(0L)
    else
      val normalizedRatios =
        if payoutRatios.isEmpty then Vector(1.0)
        else payoutRatios.map(ratio => math.max(0.0, ratio))

      val ratioSum = normalizedRatios.sum
      val effectiveRatios =
        if ratioSum <= 0.0 then Vector(1.0)
        else normalizedRatios.map(_ / ratioSum)

      val paidSlots = math.min(participantCount, effectiveRatios.size)
      val baseAwards = effectiveRatios.take(paidSlots).map(ratio => math.floor(prizePool.toDouble * ratio).toLong)
      val remainder = prizePool - baseAwards.sum
      val adjustedAwards =
        if baseAwards.isEmpty then Vector.empty
        else baseAwards.updated(0, baseAwards.head + remainder)

      adjustedAwards ++ Vector.fill(participantCount - paidSlots)(0L)

