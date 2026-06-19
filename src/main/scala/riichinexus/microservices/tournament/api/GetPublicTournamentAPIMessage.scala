package riichinexus.microservices.tournament.api

import riichinexus.microservices.tournament.objects.tablemanagement.TableStatus
import riichinexus.microservices.tournament.objects.tournamentmanagement.{TournamentFormat, TournamentStatus}

import java.util.NoSuchElementException

import cats.effect.IO
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.microservices.club.api.`private`.ResolveClubReadModelsPrivateAPIMessage
import riichinexus.microservices.club.objects.`private`.ClubPrivateView
import riichinexus.microservices.club.objects.clubmanagement.ClubId
import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.microservices.tournament.domain.stage.model.StageLineupSubmission
import riichinexus.microservices.tournament.domain.stage.functions.rules.TournamentStageQueries
import riichinexus.microservices.tournament.domain.stage.model.Table
import riichinexus.microservices.tournament.domain.competition.model.Tournament
import riichinexus.microservices.tournament.domain.stage.model.TournamentStage
import riichinexus.microservices.tournament.objects.lineupmanagement.apiTypes.TournamentLineupSubmissionView
import riichinexus.microservices.tournament.objects.rulesmanagement.knockout.KnockoutBracketSnapshot
import riichinexus.microservices.tournament.objects.rulesmanagement.ranking.StageRankingSnapshot
import riichinexus.microservices.tournament.objects.tournamentmanagement.{TournamentId, TournamentStageId}
import riichinexus.microservices.tournament.objects.tournamentmanagement.apiTypes.{PublicTournamentDetailView, PublicTournamentStageView}
import riichinexus.microservices.tournament.tables.tournamentgame.TournamentGameTable
import riichinexus.microservices.tournament.tables.tournaments.TournamentTable

import upickle.default.ReadWriter

/** 获取前端公开赛事详情。 */
final case class GetPublicTournamentAPIMessage(
    tournamentId: String
) extends APIMessage[PublicTournamentDetailView] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[PublicTournamentDetailView] =
    for
      id <- IO.blocking(TournamentId(tournamentId))
      tournament <- IO.blocking(publicTournament(context, id))
      view <- publicTournamentView(context, tournament)
    yield view

  private def publicTournament(
      context: ApiPlanContext,
      tournamentId: TournamentId
  ): Tournament =
    TournamentTable
      .findById(context.connection, tournamentId)
      .filter(_.status != TournamentStatus.Draft)
      .getOrElse(throw NoSuchElementException("Resource not found"))

  private def publicTournamentView(
      context: ApiPlanContext,
      tournament: Tournament
  ): IO[PublicTournamentDetailView] =
    for
      clubsById <- publicRelatedClubsById(context, tournament)
      tablesByStage <- IO.blocking(tablesByStageId(context, tournament))
      stages <- sequenceVector(tournament.stages.sortBy(_.order).map { stage =>
        publicStageView(context, tournament, stage, tablesByStage(stage.id))
      })
    yield PublicTournamentDetailView(
      tournamentId = tournament.id,
      name = tournament.name,
      organizer = tournament.organizer,
      status = tournament.status,
      startsAt = tournament.startsAt,
      endsAt = tournament.endsAt,
      clubIds = tournament.participatingClubs.distinct,
      playerIds = tournamentParticipantIds(tournament, clubsById),
      whitelistCount = tournament.whitelist.size,
      stages = stages
    )

  private def publicRelatedClubsById(
      context: ApiPlanContext,
      tournament: Tournament
  ): IO[Map[ClubId, ClubPrivateView]] =
    ResolveClubReadModelsPrivateAPIMessage(relatedClubIds(tournament))
      .plan(context)
      .map(_.map(club => club.id -> club).toMap)

  private def tablesByStageId(
      context: ApiPlanContext,
      tournament: Tournament
  ): Map[TournamentStageId, Vector[Table]] =
    TournamentGameTable
      .findByTournamentIds(context.connection, Vector(tournament.id))
      .groupBy(_.stageId)
      .withDefaultValue(Vector.empty)

  private def publicStageView(
      context: ApiPlanContext,
      tournament: Tournament,
      stage: TournamentStage,
      tables: Vector[Table]
  ): IO[PublicTournamentStageView] =
    for
      standings <- publicStageStandings(context, tournament, stage)
      bracket <- publicStageBracket(context, tournament, stage)
    yield PublicTournamentStageView(
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
      standings = Some(standings),
      bracket = bracket,
      advancementRule = stage.advancementRule,
      swissRule = stage.swissRule,
      knockoutRule = stage.knockoutRule,
      mahjongRuleset = stage.mahjongRuleset,
      lineupSubmissions = stage.lineupSubmissions
        .sortBy(_.submittedAt)
        .map(lineupSubmissionView)
    )

  private def lineupSubmissionView(
      submission: StageLineupSubmission
  ): TournamentLineupSubmissionView =
    TournamentLineupSubmissionView(
      submissionId = submission.id.value,
      clubId = submission.clubId.value,
      submittedBy = submission.submittedBy.value,
      submittedAt = submission.submittedAt.toString,
      activePlayerIds = submission.seats.filterNot(_.reserve).map(_.playerId.value),
      reservePlayerIds = submission.seats.filter(_.reserve).map(_.playerId.value),
      note = submission.note
    )

  private def publicStageStandings(
      context: ApiPlanContext,
      tournament: Tournament,
      stage: TournamentStage
  ): IO[StageRankingSnapshot] =
    TournamentStageQueries.stageStandings(context.connection, tournament.id, stage.id)

  private def publicStageBracket(
      context: ApiPlanContext,
      tournament: Tournament,
      stage: TournamentStage
  ): IO[Option[KnockoutBracketSnapshot]] =
    if stage.format == TournamentFormat.Knockout || stage.format == TournamentFormat.Finals then
      TournamentStageQueries.stageKnockoutBracket(context.connection, tournament.id, stage.id).map(Some(_))
    else IO.pure(None)

  private def tournamentParticipantIds(
      tournament: Tournament,
      clubsById: Map[ClubId, ClubPrivateView]
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

  private def sequenceVector[A](values: Vector[IO[A]]): IO[Vector[A]] =
    values.foldLeft(IO.pure(Vector.empty[A])) { (collected, next) =>
      for
        items <- collected
        item <- next
      yield items :+ item
    }
