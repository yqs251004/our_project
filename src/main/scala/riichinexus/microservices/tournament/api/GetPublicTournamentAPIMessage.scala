package riichinexus.microservices.tournament.api

import riichinexus.microservices.tournament.objects.tablemanagement.TableStatus
import riichinexus.microservices.tournament.objects.tournamentmanagement.{TournamentFormat, TournamentStatus}

import java.util.NoSuchElementException

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import riichinexus.system.api.{APIMessage, ApiPlanContext}
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
import riichinexus.microservices.club.domain.Club
import riichinexus.microservices.club.domain.clubmanagement.model.*
import riichinexus.microservices.club.domain.membershipmanagement.model.*
import riichinexus.microservices.club.domain.rankprivilegemanagement.model.*
import riichinexus.microservices.club.domain.relationmanagement.model.*
import riichinexus.microservices.tournament.objects.lineupmanagement.apiTypes.TournamentLineupSubmissionView
import riichinexus.microservices.tournament.objects.tournamentmanagement.apiTypes.{PublicTournamentDetailView, PublicTournamentStageView}
import riichinexus.microservices.tournament.domain.rulesmanagement.functions.TournamentStageQueries
import riichinexus.microservices.tournament.domain.lineupmanagement.model.*
import riichinexus.microservices.tournament.domain.recordmanagement.model.*
import riichinexus.microservices.tournament.domain.settlementmanagement.model.*
import riichinexus.microservices.tournament.domain.tablemanagement.model.*
import riichinexus.microservices.tournament.domain.tournamentmanagement.model.*
import riichinexus.microservices.tournament.objects.rulesmanagement.knockout.KnockoutBracketSnapshot
import riichinexus.microservices.tournament.objects.rulesmanagement.ranking.StageRankingSnapshot
import riichinexus.microservices.tournament.tables.tournaments.TournamentTable
import riichinexus.microservices.tournament.tables.tournamentgame.TournamentGameTable
import riichinexus.system.json.JsonCodecs.given
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
    ResolveClubsPrivateAPIMessage(relatedClubIds(tournament))
      .plan(ApiPlanContext(bearerToken = None, connection = context.connection))
      .unsafeRunSync()
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
      knockoutRule = stage.knockoutRule,
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
  ): StageRankingSnapshot =
    TournamentStageQueries.stageStandings(context.connection, tournament.id, stage.id)

  private def publicStageBracket(
      context: ApiPlanContext,
      tournament: Tournament,
      stage: TournamentStage
  ): Option[KnockoutBracketSnapshot] =
    if stage.format == TournamentFormat.Knockout || stage.format == TournamentFormat.Finals then
      Some(TournamentStageQueries.stageKnockoutBracket(context.connection, tournament.id, stage.id))
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
