package riichinexus.microservices.publicquery.api

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.domain.model.*
import riichinexus.microservices.publicquery.objects.apiTypes.PublicTournamentSummaryView
import riichinexus.system.objects.apiTypes.PagedResponse
import upickle.default.*

final case class ListPublicTournamentsAPIMessage(
    status: Option[String] = None,
    organizer: Option[String] = None,
    limit: Option[Int] = None,
    offset: Option[Int] = None
) extends APIMessage[PagedResponse[PublicTournamentSummaryView]] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[PagedResponse[PublicTournamentSummaryView]] =
    IO {
      val parsedStatus = status.filter(_.nonEmpty).map(
        context.support.parseEnum("status", _)(TournamentStatus.valueOf)
      )
      val tournaments = context.support.publicQueryModule.tables
        .listPublicTournaments(
          status = parsedStatus,
          organizer = organizer.filter(_.nonEmpty)
        )
        .sortBy(tournament => (tournament.startsAt, tournament.name, tournament.id.value))
      val summaries = buildPublicTournamentSummaryViews(context, tournaments)
      val resolvedLimit = limit.getOrElse(20)
      val resolvedOffset = offset.getOrElse(0)
      require(resolvedLimit > 0, "Input field limit must be positive")
      require(resolvedOffset >= 0, "Input field offset must be non-negative")
      val boundedLimit = math.min(resolvedLimit, 100)
      val page = summaries.slice(resolvedOffset, resolvedOffset + boundedLimit)

      PagedResponse(
        items = page,
        total = summaries.size,
        limit = boundedLimit,
        offset = resolvedOffset,
        hasMore = resolvedOffset + page.size < summaries.size,
        appliedFilters = Vector(
          status.filter(_.nonEmpty).map("status" -> _),
          organizer.filter(_.nonEmpty).map("organizer" -> _)
        ).flatten.toMap
      )
    }

  private def buildPublicTournamentSummaryViews(
      context: ApiPlanContext,
      tournaments: Vector[Tournament]
  ): Vector[PublicTournamentSummaryView] =
    val module = context.support.tournamentModule
    val clubsById = module.tables.findClubs(tournaments.flatMap(relatedClubIds))
      .map(club => club.id -> club)
      .toMap

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
