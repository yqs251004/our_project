package riichinexus.microservices.publicquery.tables

import riichinexus.bootstrap.TournamentModuleContext
import riichinexus.domain.model.*
import riichinexus.microservices.publicquery.objects.apiTypes.{PublicTournamentDetailView, PublicTournamentStageView, PublicTournamentSummaryView}
import riichinexus.microservices.tournament.objects.apiTypes.{
  AdvancementRuleView,
  KnockoutBracketSnapshot as KnockoutBracketSnapshotResponse,
  KnockoutRuleConfigView,
  StageRankingSnapshot as StageRankingSnapshotResponse,
  SwissRuleConfigView
}

object PublicTournamentViews:
  def detail(
      module: TournamentModuleContext,
      tournamentId: TournamentId
  ): Option[PublicTournamentDetailView] =
    module.tables.findTournament(tournamentId)
      .filter(_.status != TournamentStatus.Draft)
      .map { tournament =>
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
            publicStageView(module, tournament, stage, tablesByStage(stage.id))
          }
        )
      }

  def summaries(
      module: TournamentModuleContext,
      tournaments: Vector[Tournament]
  ): Vector[PublicTournamentSummaryView] =
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

  private def publicStageView(
      module: TournamentModuleContext,
      tournament: Tournament,
      stage: TournamentStage,
      tables: Vector[Table]
  ): PublicTournamentStageView =
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
