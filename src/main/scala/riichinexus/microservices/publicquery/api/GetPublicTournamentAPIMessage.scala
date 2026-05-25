package riichinexus.microservices.publicquery.api

import java.util.NoSuchElementException

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.domain.model.*
import riichinexus.microservices.publicquery.objects.apiTypes.{PublicTournamentDetailView, PublicTournamentStageView}
import riichinexus.microservices.tournament.objects.apiTypes.{
  AdvancementRuleView,
  KnockoutBracketSnapshot as KnockoutBracketSnapshotResponse,
  KnockoutRuleConfigView,
  StageRankingSnapshot as StageRankingSnapshotResponse,
  SwissRuleConfigView
}
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
        tournamentId = tournament.id.value,
        name = tournament.name,
        organizer = tournament.organizer,
        status = tournament.status.toString,
        startsAt = tournament.startsAt.toString,
        endsAt = tournament.endsAt.toString,
        clubIds = tournament.participatingClubs.distinct.map(_.value),
        playerIds = tournamentParticipantIds(tournament, clubsById).map(_.value),
        whitelistCount = tournament.whitelist.size,
        stages = tournament.stages.sortBy(_.order).map { stage =>
          val tables = tablesByStage(stage.id)
          val bracket =
            if stage.format == StageFormat.Knockout || stage.format == StageFormat.Finals then
              Some(KnockoutBracketSnapshotResponse.fromDomain(module.stageQueries.stageKnockoutBracket(tournament.id, stage.id)))
            else None

          PublicTournamentStageView(
            stageId = stage.id.value,
            name = stage.name,
            format = stage.format.toString,
            order = stage.order,
            status = stage.status.toString,
            currentRound = stage.currentRound,
            roundCount = stage.roundCount,
            schedulingPoolSize = stage.schedulingPoolSize,
            tableCount = tables.size,
            archivedTableCount = tables.count(_.status == TableStatus.Archived),
            pendingTablePlanCount = stage.pendingTablePlans.size,
            standings = Some(StageRankingSnapshotResponse.fromDomain(module.stageQueries.stageStandings(tournament.id, stage.id))),
            bracket = bracket,
            advancementRule = AdvancementRuleView.fromDomain(stage.advancementRule),
            swissRule = stage.swissRule.map(SwissRuleConfigView.fromDomain),
            knockoutRule = stage.knockoutRule.map(KnockoutRuleConfigView.fromDomain)
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
