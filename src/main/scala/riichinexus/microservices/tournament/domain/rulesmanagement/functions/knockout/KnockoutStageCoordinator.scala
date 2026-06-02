package riichinexus.microservices.tournament.domain.rulesmanagement.functions.knockout
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
import java.sql.Connection
import java.time.Instant
import java.util.NoSuchElementException

import cats.effect.unsafe.implicits.global
import riichinexus.system.api.ApiPlanContext
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
import riichinexus.microservices.club.api.`private`.ResolveClubsPrivateAPIMessage
import riichinexus.microservices.tournament.domain.tablemanagement.functions.TableFunctions
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
import riichinexus.microservices.player.domain.Player
import riichinexus.microservices.player.objects.*
import riichinexus.microservices.player.api.{CreatePlayerAPIMessage, GetPlayerAPIMessage, ListPlayersAPIMessage}
import riichinexus.microservices.tournament.objects.rulesmanagement.knockout.KnockoutBracketSnapshot
import riichinexus.microservices.tournament.objects.tablemanagement.{SeatWind, TableSeat, TableStatus}
import riichinexus.microservices.tournament.objects.tournamentmanagement.TournamentStatus
import riichinexus.microservices.tournament.tables.matchrecord.MatchRecordTable
import riichinexus.microservices.tournament.tables.tournaments.TournamentTable
import riichinexus.microservices.tournament.tables.tournamentgame.TournamentGameTable

object KnockoutStageCoordinator:
  def buildProgression(
      connection: Connection,
      tournamentId: TournamentId,
      stageId: TournamentStageId,
      at: Instant = Instant.now()
  ): KnockoutBracketSnapshot =
    val tournament = TournamentTable
      .findById(connection, tournamentId)
      .getOrElse(throw NoSuchElementException(s"Tournament ${tournamentId.value} was not found"))
    val stage = requireStage(tournament, stageId)
    val participants = resolveParticipants(connection, tournament, stage)
    val records = stageRecords(connection, tournamentId, stageId)
    buildProgression(
      tournament = tournament,
      stage = stage,
      participants = participants,
      records = records,
      tables = TournamentGameTable.findByTournamentAndStage(connection, tournamentId, stageId),
      at = at
    )

  def buildProgression(
      tournament: Tournament,
      stage: TournamentStage,
      participants: Vector[Player],
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
  ): Vector[Table] =
    {
      val tournament = TournamentTable
        .findById(connection, tournamentId)
        .getOrElse(throw NoSuchElementException(s"Tournament ${tournamentId.value} was not found"))
      val stage = requireStage(tournament, stageId)
      val existingTables = TournamentGameTable.findByTournamentAndStage(connection, tournamentId, stageId)
      val participants = resolveParticipants(connection, tournament, stage)
      val participantsById = participants.map(player => player.id -> player).toMap
      val records = stageRecords(connection, tournamentId, stageId)
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

  def reconcileAfterMatchMutation(
      connection: Connection,
      tournamentId: TournamentId,
      stageId: TournamentStageId,
      mutatedMatchId: String,
      at: Instant = Instant.now()
  ): Vector[Table] =
    {
      pruneDependentTables(connection, tournamentId, stageId, mutatedMatchId)
      materializeUnlockedTables(connection, tournamentId, stageId, at)
    }

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
