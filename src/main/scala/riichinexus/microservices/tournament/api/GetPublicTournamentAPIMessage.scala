package riichinexus.microservices.tournament.api

import java.util.NoSuchElementException

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.domain.model.*
import riichinexus.microservices.club.domain.model.*
import riichinexus.microservices.club.tables.club.ClubTable
import riichinexus.microservices.tournament.objects.apiTypes.{PublicTournamentDetailView, PublicTournamentStageView}
import riichinexus.microservices.tournament.domain.TournamentStageQueries
import riichinexus.microservices.tournament.domain.model.*
import riichinexus.microservices.tournament.objects.{
  AdvancementRuleView,
  KnockoutBracketSnapshot as KnockoutBracketSnapshotResponse,
  KnockoutRuleConfigView,
  StageRankingSnapshot as StageRankingSnapshotResponse,
  SwissRuleConfigView,
  TournamentFormat
}
import riichinexus.microservices.tournament.tables.tournament.TournamentTable
import riichinexus.microservices.tournament.tables.tournamentgame.TournamentGameTable
import upickle.default.*

final case class GetPublicTournamentAPIMessage(
    tournamentId: String
) extends APIMessage[PublicTournamentDetailView] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[PublicTournamentDetailView] =
    for
      id <- IO.blocking(TournamentId(tournamentId))
      tournament <- IO.blocking(publicTournament(context, id))
      clubsById <- IO.blocking(publicRelatedClubsById(context, tournament))
      tablesByStage <- IO.blocking(tablesByStageId(context, tournament))
    yield publicTournamentView(context, tournament, clubsById, tablesByStage)

  private def publicTournament(
      context: ApiPlanContext,
      tournamentId: TournamentId
  ): Tournament =
    TournamentTable
      .findById(context.connection, tournamentId)
      .filter(_.status != TournamentStatus.Draft)
      .getOrElse(throw NoSuchElementException("Resource not found"))

  private def publicRelatedClubsById(
      context: ApiPlanContext,
      tournament: Tournament
  ): Map[ClubId, Club] =
    ClubTable
      .findByIds(context.connection, relatedClubIds(tournament))
      .map(club => club.id -> club)
      .toMap

  private def tablesByStageId(
      context: ApiPlanContext,
      tournament: Tournament
  ): Map[TournamentStageId, Vector[Table]] =
    TournamentGameTable
      .findByTournamentIds(context.connection, Vector(tournament.id))
      .groupBy(_.stageId)
      .withDefaultValue(Vector.empty)

  private def publicTournamentView(
      context: ApiPlanContext,
      tournament: Tournament,
      clubsById: Map[ClubId, Club],
      tablesByStage: Map[TournamentStageId, Vector[Table]]
  ): PublicTournamentDetailView =
    PublicTournamentDetailView(
      tournamentId = tournament.id,
      name = tournament.name,
      organizer = tournament.organizer,
      status = tournament.status,
      startsAt = tournament.startsAt,
      endsAt = tournament.endsAt,
      clubIds = tournament.participatingClubs.distinct,
      playerIds = tournamentParticipantIds(tournament, clubsById),
      whitelistCount = tournament.whitelist.size,
      stages = tournament.stages.sortBy(_.order).map { stage =>
        publicStageView(context, tournament, stage, tablesByStage(stage.id))
      }
    )

  private def publicStageView(
      context: ApiPlanContext,
      tournament: Tournament,
      stage: TournamentStage,
      tables: Vector[Table]
  ): PublicTournamentStageView =
    PublicTournamentStageView(
      stageId = stage.id,
      name = stage.name,
      format = stage.format,
      order = stage.order,
      status = stage.status,
      currentRound = stage.currentRound,
      roundCount = stage.roundCount,
      schedulingPoolSize = stage.schedulingPoolSize,
      tableCount = tables.size,
      archivedTableCount = tables.count(_.status == TableStatus.Archived),
      pendingTablePlanCount = stage.pendingTablePlans.size,
      standings = Some(publicStageStandings(context, tournament, stage)),
      bracket = publicStageBracket(context, tournament, stage),
      advancementRule = stage.advancementRule,
      swissRule = stage.swissRule,
      knockoutRule = stage.knockoutRule
    )

  private def publicStageStandings(
      context: ApiPlanContext,
      tournament: Tournament,
      stage: TournamentStage
  ): StageRankingSnapshotResponse =
    StageRankingSnapshotResponse.fromDomain(
      TournamentStageQueries.stageStandings(context.connection, tournament.id, stage.id)
    )

  private def publicStageBracket(
      context: ApiPlanContext,
      tournament: Tournament,
      stage: TournamentStage
  ): Option[KnockoutBracketSnapshotResponse] =
    if stage.format == StageFormat.Knockout || stage.format == StageFormat.Finals then
      Some(
        KnockoutBracketSnapshotResponse.fromDomain(
          TournamentStageQueries.stageKnockoutBracket(context.connection, tournament.id, stage.id)
        )
      )
    else None

  private def tournamentParticipantIds(
      tournament: Tournament,
      clubsById: Map[ClubId, Club]
  ): Vector[PlayerId] =
    val clubMembers = tournament.participatingClubs.flatMap(clubId =>
      clubsById.get(clubId).toVector.flatMap(_.members)
    )
    val whitelistedClubMembers = tournament.whitelist.flatMap(entry =>
      entry.clubId.toVector.flatMap(clubId => clubsById.get(clubId).toVector.flatMap(_.members))
    )

    (tournament.participatingPlayers ++ tournament.whitelist.flatMap(_.playerId) ++ clubMembers ++ whitelistedClubMembers)
      .distinct

  private def relatedClubIds(tournament: Tournament): Vector[ClubId] =
    (tournament.participatingClubs ++ tournament.whitelist.flatMap(_.clubId)).distinct
