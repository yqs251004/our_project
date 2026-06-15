package riichinexus.microservices.tournament.api

import riichinexus.microservices.tournament.objects.tournamentmanagement.{StageStatus, TournamentStatus}

import cats.effect.IO
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
import riichinexus.microservices.tournament.objects.tournamentmanagement.apiTypes.PublicTournamentSummaryView
import riichinexus.microservices.tournament.domain.lineupmanagement.model.*
import riichinexus.microservices.tournament.domain.recordmanagement.model.*
import riichinexus.microservices.tournament.domain.settlementmanagement.model.*
import riichinexus.microservices.tournament.domain.tablemanagement.model.*
import riichinexus.microservices.tournament.domain.tournamentmanagement.model.*
import riichinexus.microservices.tournament.tables.tournaments.TournamentTable
import riichinexus.system.objects.PagedResponse
import riichinexus.system.json.JsonCodecs.given
import upickle.default.*

final case class ListPublicTournamentsAPIMessage(
    status: Option[String] = None,
    organizer: Option[String] = None,
    limit: Option[Int] = None,
    offset: Option[Int] = None
) extends APIMessage[PagedResponse[PublicTournamentSummaryView]] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[PagedResponse[PublicTournamentSummaryView]] =
    for
      query <- IO.blocking(resolveQuery(context))
      tournaments <- IO.blocking(publicTournaments(context, query))
      clubsById <- publicRelatedClubsById(context, tournaments)
      summaries <- IO.blocking(publicTournamentSummaryViews(tournaments, clubsById))
    yield PagedResponse.fromItems(summaries, limit, offset, query.appliedFilters)(identity)

  private def resolveQuery(context: ApiPlanContext): ResolvedPublicTournamentsQuery =
    ResolvedPublicTournamentsQuery(
      status = status.filter(_.nonEmpty).map(
        riichinexus.system.EnumParsing.parse("status", _)(TournamentStatus.valueOf)
      ),
      organizer = organizer.filter(_.nonEmpty),
      appliedFilters = Vector(
        status.filter(_.nonEmpty).map("status" -> _),
        organizer.filter(_.nonEmpty).map("organizer" -> _)
      ).flatten.toMap
    )

  private def publicTournaments(
      context: ApiPlanContext,
      query: ResolvedPublicTournamentsQuery
  ): Vector[Tournament] =
    TournamentTable
      .findFiltered(
        connection = context.connection,
        status = query.status,
        organizer = query.organizer,
        includeDraft = false
      )
      .sortBy(tournament => (tournament.startsAt, tournament.name, tournament.id.value))

  private def publicRelatedClubsById(
      context: ApiPlanContext,
      tournaments: Vector[Tournament]
  ): IO[Map[ClubId, Club]] =
    ResolveClubsPrivateAPIMessage(tournaments.flatMap(relatedClubIds))
      .plan(context)
      .map(_.map(club => club.id -> club).toMap)

  private def publicTournamentSummaryViews(
      tournaments: Vector[Tournament],
      clubsById: Map[ClubId, Club]
  ): Vector[PublicTournamentSummaryView] =
    tournaments.map { tournament =>
      PublicTournamentSummaryView(
        tournamentId = tournament.id,
        name = tournament.name,
        organizer = tournament.organizer,
        status = tournament.status,
        startsAt = tournament.startsAt,
        endsAt = tournament.endsAt,
        stageCount = tournament.stages.size,
        activeStageCount = tournament.stages.count(stage =>
          stage.status == StageStatus.Active || stage.status == StageStatus.Ready
        ),
        participantCount = tournamentParticipantIds(tournament, clubsById).size,
        clubCount = tournament.participatingClubs.distinct.size,
        playerCount = tournament.participatingPlayers.distinct.size
      )
    }

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

  private final case class ResolvedPublicTournamentsQuery(
      status: Option[TournamentStatus],
      organizer: Option[String],
      appliedFilters: Map[String, String]
  )
