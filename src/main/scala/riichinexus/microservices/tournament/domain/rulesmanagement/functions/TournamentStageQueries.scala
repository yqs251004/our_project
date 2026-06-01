package riichinexus.microservices.tournament.domain.rulesmanagement.functions

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
import riichinexus.api.ApiPlanContext
import riichinexus.domain.model.*
import riichinexus.microservices.club.api.`private`.ResolveClubsPrivateAPIMessage
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
import riichinexus.microservices.tournament.objects.rulesmanagement.stageprogression.StageAdvancementSnapshot
import riichinexus.microservices.tournament.objects.rulesmanagement.ranking.StageRankingSnapshot
import riichinexus.microservices.tournament.tables.matchrecord.MatchRecordTable
import riichinexus.microservices.tournament.tables.tournaments.TournamentTable
import riichinexus.microservices.tournament.tables.tournamentgame.TournamentGameTable

object TournamentStageQueries:
  def stageStandings(
      connection: Connection,
      tournamentId: TournamentId,
      stageId: TournamentStageId,
      at: Instant = Instant.now()
  ): StageRankingSnapshot =
    val context = stageComputationContext(connection, tournamentId, stageId)
    TournamentRuleEngine.buildStageRanking(
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
    val ranking = TournamentRuleEngine.buildStageRanking(
      context.tournament,
      context.stage,
      context.participants.map(_.id),
      context.records,
      at
    )
    TournamentRuleEngine.projectAdvancement(context.tournament, context.stage, ranking, at)

  def stageKnockoutBracket(
      connection: Connection,
      tournamentId: TournamentId,
      stageId: TournamentStageId,
      at: Instant = Instant.now()
  ): KnockoutBracketSnapshot =
    val context = stageComputationContext(connection, tournamentId, stageId)
    KnockoutStageCoordinator.buildProgression(
      tournament = context.tournament,
      stage = context.stage,
      participants = context.participants,
      records = context.records,
      tables = stageTables(connection, tournamentId, stageId),
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
    val clubIds = (tournament.participatingClubs ++ tournament.whitelist.flatMap(_.clubId)).distinct
    val clubsById = ResolveClubsPrivateAPIMessage(clubIds)
      .plan(ApiPlanContext(support = null, bearerToken = None, connection = connection))
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

    val playersById = ListPlayersAPIMessage.findPlayersByIds(connection, (stage.lineupSubmissions.flatMap(_.seats.map(_.playerId)) ++ fallbackPlayerIds).distinct)
      .map(player => player.id -> player)
      .toMap
    val stagePlayerIds = StageLineupResolver.resolveEligiblePlayers(stage, playersById.get)

    val targetPlayerIds =
      StageLineupResolver.resolveTargetPlayerIds(tournament, stagePlayerIds, fallbackPlayerIds)

    targetPlayerIds.flatMap { playerId =>
      playersById.get(playerId).filter(_.status == PlayerStatus.Active)
    }

  private def stageTables(
      connection: Connection,
      tournamentId: TournamentId,
      stageId: TournamentStageId
  ): Vector[Table] =
    TournamentGameTable.findByTournamentAndStage(connection, tournamentId, stageId)
      .sortBy(table => (table.stageRoundNumber, table.tableNo, table.id.value))
