package riichinexus.microservices.tournament.api

import riichinexus.microservices.tournament.objects.stage.StageStatus
import riichinexus.microservices.tournament.objects.competition.TournamentStatus

import cats.effect.IO
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.microservices.club.objects.clubmanagement.ClubId
import riichinexus.microservices.club.api.`private`.ResolveClubReadModelsPrivateAPIMessage
import riichinexus.microservices.club.objects.`private`.ClubPrivateView
import riichinexus.microservices.tournament.objects.competition.apiTypes.PublicTournamentSummaryView
import riichinexus.microservices.tournament.domain.competition.model.Tournament
import riichinexus.microservices.tournament.tables.tournaments.TournamentTable
import riichinexus.system.objects.PagedResponse
import riichinexus.system.json.JsonCodecs.given
import upickle.default.ReadWriter

/** 列出前端公开赛事。 */
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
  ): IO[Map[ClubId, ClubPrivateView]] =
    ResolveClubReadModelsPrivateAPIMessage(tournaments.flatMap(relatedClubIds))
      .plan(context)
      .map(_.map(club => club.id -> club).toMap)

  private def publicTournamentSummaryViews(
      tournaments: Vector[Tournament],
      clubsById: Map[ClubId, ClubPrivateView]
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

  private final case class ResolvedPublicTournamentsQuery(
      status: Option[TournamentStatus],
      organizer: Option[String],
      appliedFilters: Map[String, String]
  )
