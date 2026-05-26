package riichinexus.microservices.tournament.domain

import java.sql.Connection
import java.time.Instant
import java.util.NoSuchElementException

import riichinexus.domain.model.*
import riichinexus.microservices.club.domain.model.*
import riichinexus.microservices.player.objects.*
import riichinexus.domain.service.TournamentRuleEngine
import riichinexus.microservices.club.tables.club.ClubTable
import riichinexus.microservices.player.tables.player.PlayerTable
import riichinexus.microservices.tournament.objects.apiTypes.StageTableQuery
import riichinexus.microservices.tournament.tables.matchrecord.MatchRecordTable
import riichinexus.microservices.tournament.tables.tournament.TournamentTable
import riichinexus.microservices.tournament.tables.tournamentgame.TournamentGameTable

final class TournamentStageQueryService(
    tournamentRuleEngine: TournamentRuleEngine,
    knockoutStageCoordinator: KnockoutStageCoordinator
):
  def stageStandings(
      connection: Connection,
      tournamentId: TournamentId,
      stageId: TournamentStageId,
      at: Instant = Instant.now()
  ): StageRankingSnapshot =
    val context = stageComputationContext(connection, tournamentId, stageId)
    tournamentRuleEngine.buildStageRanking(
      context.tournament,
      context.stage,
      context.participants.map(_.id),
      context.records,
      at
    )

  def stageAdvancementPreview(
      connection: Connection,
      tournamentId: TournamentId,
      stageId: TournamentStageId,
      at: Instant = Instant.now()
  ): StageAdvancementSnapshot =
    val context = stageComputationContext(connection, tournamentId, stageId)
    val ranking = tournamentRuleEngine.buildStageRanking(
      context.tournament,
      context.stage,
      context.participants.map(_.id),
      context.records,
      at
    )
    tournamentRuleEngine.projectAdvancement(context.tournament, context.stage, ranking, at)

  def stageKnockoutBracket(
      connection: Connection,
      tournamentId: TournamentId,
      stageId: TournamentStageId,
      at: Instant = Instant.now()
  ): KnockoutBracketSnapshot =
    val context = stageComputationContext(connection, tournamentId, stageId)
    knockoutStageCoordinator.buildProgression(
      tournament = context.tournament,
      stage = context.stage,
      participants = context.participants,
      records = context.records,
      tables = listStageTables(connection, tournamentId, stageId, StageTableQuery()),
      at = at
    )

  private final case class StageComputationContext(
      tournament: Tournament,
      stage: TournamentStage,
      participants: Vector[Player],
      records: Vector[MatchRecord]
  )

  private def stageComputationContext(
      connection: Connection,
      tournamentId: TournamentId,
      stageId: TournamentStageId
  ): StageComputationContext =
    val tournament = TournamentTable.findById(connection, tournamentId)
      .getOrElse(throw NoSuchElementException(s"Tournament ${tournamentId.value} was not found"))
    val stage = tournament.stages
      .find(_.id == stageId)
      .getOrElse(throw NoSuchElementException(s"Stage ${stageId.value} was not found"))
    StageComputationContext(
      tournament = tournament,
      stage = stage,
      participants = resolveParticipants(connection, tournament, stage),
      records = MatchRecordTable.findByTournamentAndStage(connection, tournamentId, stageId)
    )

  private def resolveParticipants(
      connection: Connection,
      tournament: Tournament,
      stage: TournamentStage
  ): Vector[Player] =
    val clubsById = ClubTable.findByIds(
      connection,
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
    val stagePlayerIds = StageLineupSupport.resolveEligiblePlayers(stage, playersById.get)

    val targetPlayerIds =
      StageLineupSupport.resolveTargetPlayerIds(tournament, stagePlayerIds, fallbackPlayerIds)

    targetPlayerIds.flatMap { playerId =>
      playersById.get(playerId).filter(_.status == PlayerStatus.Active)
    }

  private def listStageTables(
      connection: Connection,
      tournamentId: TournamentId,
      stageId: TournamentStageId,
      query: StageTableQuery
  ): Vector[Table] =
    TournamentGameTable.findByTournamentAndStage(connection, tournamentId, stageId)
      .filter(table => query.status.forall(_ == table.status))
      .filter(table => query.roundNumber.forall(_ == table.stageRoundNumber))
      .filter(table => query.playerId.forall(playerId => table.seats.exists(_.playerId == playerId)))
      .sortBy(table => (table.stageRoundNumber, table.tableNo, table.id.value))
