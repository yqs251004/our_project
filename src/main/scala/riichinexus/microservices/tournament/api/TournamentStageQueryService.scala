package riichinexus.microservices.tournament.api

import java.time.Instant
import java.util.NoSuchElementException

import riichinexus.domain.model.*
import riichinexus.domain.service.TournamentRuleEngine
import riichinexus.microservices.tournament.objects.StageTableQuery
import riichinexus.microservices.tournament.tables.TournamentTables

final class TournamentStageQueryService(
    tables: TournamentTables,
    tournamentRuleEngine: TournamentRuleEngine,
    knockoutStageCoordinator: KnockoutStageCoordinator
):
  def stageStandings(
      tournamentId: TournamentId,
      stageId: TournamentStageId,
      at: Instant = Instant.now()
  ): StageRankingSnapshot =
    val context = stageComputationContext(tournamentId, stageId)
    tournamentRuleEngine.buildStageRanking(
      context.tournament,
      context.stage,
      context.participants.map(_.id),
      context.records,
      at
    )

  def stageAdvancementPreview(
      tournamentId: TournamentId,
      stageId: TournamentStageId,
      at: Instant = Instant.now()
  ): StageAdvancementSnapshot =
    val context = stageComputationContext(tournamentId, stageId)
    val ranking = tournamentRuleEngine.buildStageRanking(
      context.tournament,
      context.stage,
      context.participants.map(_.id),
      context.records,
      at
    )
    tournamentRuleEngine.projectAdvancement(context.tournament, context.stage, ranking, at)

  def stageKnockoutBracket(
      tournamentId: TournamentId,
      stageId: TournamentStageId,
      at: Instant = Instant.now()
  ): KnockoutBracketSnapshot =
    val context = stageComputationContext(tournamentId, stageId)
    knockoutStageCoordinator.buildProgression(
      tournament = context.tournament,
      stage = context.stage,
      participants = context.participants,
      records = context.records,
      tables = tables.listStageTables(tournamentId, stageId, StageTableQuery()),
      at = at
    )

  private final case class StageComputationContext(
      tournament: Tournament,
      stage: TournamentStage,
      participants: Vector[Player],
      records: Vector[MatchRecord]
  )

  private def stageComputationContext(
      tournamentId: TournamentId,
      stageId: TournamentStageId
  ): StageComputationContext =
    val tournament = tables.findTournament(tournamentId)
      .getOrElse(throw NoSuchElementException(s"Tournament ${tournamentId.value} was not found"))
    val stage = tournament.stages
      .find(_.id == stageId)
      .getOrElse(throw NoSuchElementException(s"Stage ${stageId.value} was not found"))
    StageComputationContext(
      tournament = tournament,
      stage = stage,
      participants = resolveParticipants(tournament, stage),
      records = tables.listStageMatchRecords(tournamentId, stageId)
    )

  private def resolveParticipants(
      tournament: Tournament,
      stage: TournamentStage
  ): Vector[Player] =
    val clubsById = tables.findClubs(
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

    val playersById = tables.findPlayers(
      (stage.lineupSubmissions.flatMap(_.seats.map(_.playerId)) ++ fallbackPlayerIds).distinct
    ).map(player => player.id -> player).toMap
    val stagePlayerIds = StageLineupSupport.resolveEligiblePlayers(stage, playersById.get)

    val targetPlayerIds =
      if stagePlayerIds.nonEmpty then stagePlayerIds else fallbackPlayerIds

    targetPlayerIds.flatMap { playerId =>
      playersById.get(playerId).filter(_.status == PlayerStatus.Active)
    }
