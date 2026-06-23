package riichinexus.microservices.tournament.api.competition
import riichinexus.microservices.tournament.objects.stage.lifecycle.StageStatus
import riichinexus.microservices.tournament.objects.competition.TournamentStatus

import cats.effect.IO
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.microservices.player.objects.PlayerId
import riichinexus.microservices.club.objects.profile.ClubId
import riichinexus.microservices.club.api.audit.`private`.ResolveClubReadModelsPrivateAPIMessage
import riichinexus.microservices.club.objects.profile.`private`.ClubPrivateView
import riichinexus.microservices.tournament.objects.competition.PublicTournamentSummaryView
import riichinexus.microservices.tournament.domain.competition.model.Tournament
import riichinexus.microservices.tournament.domain.competition.functions.TournamentViewFunctions
import riichinexus.microservices.tournament.tables.tournaments.TournamentTable
import riichinexus.system.objects.{PagedResponse, QueryFilterField}
import riichinexus.system.json.JsonCodecs.given
/** 列出前端公开赛事。 */
final case class ListPublicTournamentsAPIMessage(
    status: Option[String] = None,
    organizer: Option[String] = None,
    limit: Option[Int] = None,
    offset: Option[Int] = None
) extends APIMessage[PagedResponse[PublicTournamentSummaryView]]:

  override def plan(context: ApiPlanContext): IO[PagedResponse[PublicTournamentSummaryView]] =
    for
      statusFilter <- IO.blocking(
        status.filter(_.nonEmpty).map(
          riichinexus.system.EnumParsing.parse(QueryFilterField.toString(QueryFilterField.Status), _)(TournamentStatus.valueOf)
        )
      )
      organizerFilter = organizer.filter(_.nonEmpty)
      appliedFilters = Vector(
        status.filter(_.nonEmpty).map(QueryFilterField.toString(QueryFilterField.Status) -> _),
        organizerFilter.map(QueryFilterField.toString(QueryFilterField.Organizer) -> _)
      ).flatten.toMap
      tournaments <- IO.blocking(publicTournaments(context, statusFilter, organizerFilter))
      clubsById <- publicRelatedClubsById(context, tournaments)
      summaries <- IO.blocking(publicTournamentSummaryViews(tournaments, clubsById))
    yield PagedResponse.fromItems(summaries, limit, offset, appliedFilters)(identity)

  private def publicTournaments(
      context: ApiPlanContext,
      status: Option[TournamentStatus],
      organizer: Option[String]
  ): Vector[Tournament] =
    TournamentTable
      .findFiltered(
        connection = context.connection,
        status = status,
        organizer = organizer,
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
      TournamentViewFunctions.publicTournamentSummaryView(
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
