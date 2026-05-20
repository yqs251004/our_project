package riichinexus.microservices.publicquery.api

import java.util.NoSuchElementException

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.domain.model.*
import riichinexus.microservices.publicquery.objects.apiTypes.{PublicTournamentDetailView, PublicTournamentStageView}
import upickle.default.*

final case class GetPublicTournamentAPIMessage(
    tournamentId: String
) extends APIMessage[PublicTournamentDetailView] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[PublicTournamentDetailView] =
    IO {
      val module = context.support.tournamentModule
      val parsedTournamentId = TournamentId(tournamentId)
      val tournament = module.tables.findTournament(parsedTournamentId)
        .filter(_.status != TournamentStatus.Draft)
        .getOrElse(throw NoSuchElementException("Resource not found"))
      val clubsById = module.tables.findClubs(relatedClubIds(tournament))
        .map(club => club.id -> club)
        .toMap
      val tablesByStage = module.tables.listTournamentTables(tournament.id)
        .groupBy(_.stageId)
        .withDefaultValue(Vector.empty)

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
          val tables = tablesByStage(stage.id)
          val bracket =
            if stage.format == StageFormat.Knockout || stage.format == StageFormat.Finals then
              Some(module.stageQueries.stageKnockoutBracket(tournament.id, stage.id))
            else None

          PublicTournamentStageView(
            stageId = stage.id,
            name = stage.name,
            format = stage.format,
            order = stage.order,
            status = stage.status,
            currentRound = stage.currentRound,
            roundCount = stage.roundCount,
            tableCount = tables.size,
            archivedTableCount = tables.count(_.status == TableStatus.Archived),
            pendingTablePlanCount = stage.pendingTablePlans.size,
            standings = Some(module.stageQueries.stageStandings(tournament.id, stage.id)),
            bracket = bracket
          )
        }
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
