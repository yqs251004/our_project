package riichinexus.microservices.tournament.domain

import java.sql.Connection

import riichinexus.bootstrap.TournamentModuleContext
import riichinexus.domain.model.*
import riichinexus.microservices.tournament.domain.model.*
import riichinexus.microservices.club.domain.model.*
import riichinexus.microservices.player.objects.*
import riichinexus.microservices.club.tables.club.ClubTable
import riichinexus.microservices.player.tables.player.PlayerTable
import riichinexus.microservices.tournament.objects.TournamentFormat
import riichinexus.microservices.tournament.objects.apiTypes.{AdvancementRuleView, KnockoutRuleConfigView, SwissRuleConfigView}
import riichinexus.microservices.tournament.objects.apiTypes.*
import riichinexus.microservices.tournament.tables.tournament.TournamentTable

object TournamentOperationViewAssembler:
  def mutationView(
      connection: Connection,
      module: TournamentModuleContext,
      tournamentId: TournamentId,
      scheduledTables: Vector[Table]
  ): Option[TournamentMutationView] =
    detailView(connection, module, tournamentId).map(detail =>
      TournamentMutationView(
        tournament = detail,
        scheduledTables = scheduledTables
          .sortBy(table => (table.stageRoundNumber, table.tableNo, table.id.value))
          .map(TournamentTableView.fromDomain)
      )
    )

  def detailView(
      connection: Connection,
      module: TournamentModuleContext,
      tournamentId: TournamentId
  ): Option[TournamentDetailView] =
    TournamentTable.findById(connection, tournamentId).map(tournament =>
      detailView(connection, module, tournament)
    )

  def detailView(
      connection: Connection,
      module: TournamentModuleContext,
      tournament: Tournament
  ): TournamentDetailView =
    val tournamentClubIds = relatedClubIds(tournament)
    val clubsById = ClubTable.findByIds(connection, tournamentClubIds)
      .map(club => club.id -> club)
      .toMap
    val participantIds = tournamentParticipantIds(tournament, clubsById)
    val playerIdsForLookup = (
      tournament.participatingClubs.distinct.flatMap(clubId => clubsById.get(clubId).toVector.flatMap(_.members)) ++
        participantIds ++
        tournament.stages.flatMap(_.lineupSubmissions.map(_.submittedBy))
    ).distinct
    val playersById = PlayerTable.findByIds(connection, playerIdsForLookup.toVector.distinct)
      .map(player => player.id -> player)
      .toMap

    val participatingClubs = tournament.participatingClubs.distinct.flatMap { clubId =>
      clubsById.get(clubId).map { club =>
        TournamentParticipantClubView(
          clubId = club.id,
          memberCount = club.members.size
        )
      }
    }.sortBy(_.clubId)

    val participatingPlayers = participantIds.flatMap { playerId =>
      playersById.get(playerId).map { player =>
        TournamentParticipantPlayerView(
          playerId = player.id,
          nickname = player.nickname,
          status = player.status,
          elo = player.elo,
          currentRank = player.currentRank,
          clubIds = player.boundClubIds
        )
      }
    }.sortBy(player => (player.nickname, player.playerId))

    val whitelistedClubIds = tournament.whitelist.flatMap(_.clubId).distinct.sortBy(_.value)
    val whitelistedPlayerIds = tournament.whitelist.flatMap(_.playerId).distinct.sortBy(_.value)

    TournamentDetailView(
      tournamentId = tournament.id,
      name = tournament.name,
      organizer = tournament.organizer,
      status = tournament.status,
      startsAt = tournament.startsAt,
      endsAt = tournament.endsAt,
      participatingClubs = participatingClubs,
      participatingPlayers = participatingPlayers,
      whitelistSummary = TournamentWhitelistSummaryView(
        totalEntries = tournament.whitelist.size,
        clubCount = whitelistedClubIds.size,
        playerCount = whitelistedPlayerIds.size,
        clubIds = whitelistedClubIds.map(_.value),
        playerIds = whitelistedPlayerIds.map(_.value)
      ),
      stages = tournament.stages.sortBy(_.order).map(stage =>
        operationsStageView(stage, clubsById, playersById)
      )
    )

  private def operationsStageView(
      stage: TournamentStage,
      clubsById: Map[ClubId, Club],
      playersById: Map[PlayerId, Player]
  ): TournamentOperationsStageView =
    TournamentOperationsStageView(
      stageId = stage.id,
      name = stage.name,
      format = stage.format,
      order = stage.order,
      status = stage.status,
      currentRound = stage.currentRound,
      roundCount = stage.roundCount,
      schedulingPoolSize = stage.schedulingPoolSize,
      pendingTablePlanCount = stage.pendingTablePlans.size,
      scheduledTableCount = stage.scheduledTableIds.size,
      advancementRule = stage.advancementRule,
      swissRule = stage.swissRule,
      knockoutRule = stage.knockoutRule,
      lineupSubmissions = stage.lineupSubmissions
        .sortBy(_.submittedAt)
        .map(submission => lineupSubmissionView(submission, clubsById, playersById))
    )

  private def lineupSubmissionView(
      submission: StageLineupSubmission,
      clubsById: Map[ClubId, Club],
      playersById: Map[PlayerId, Player]
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
