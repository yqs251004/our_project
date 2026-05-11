package routes

import riichinexus.bootstrap.ApplicationContext
import riichinexus.domain.model.*

trait TournamentViewSupport extends ViewLookupSupport:
  protected def app: ApplicationContext
  def canManageClubTournamentParticipation(actor: AccessPrincipal, club: Club): Boolean

  def buildTournamentStageDirectoryEntry(stage: TournamentStage): TournamentStageDirectoryEntry =
    TournamentStageDirectoryEntry(
      stageId = stage.id,
      name = stage.name,
      format = stage.format,
      order = stage.order,
      status = stage.status,
      currentRound = stage.currentRound,
      roundCount = stage.roundCount,
      schedulingPoolSize = stage.schedulingPoolSize,
      pendingTablePlanCount = stage.pendingTablePlans.size,
      scheduledTableCount = stage.scheduledTableIds.size
    )

  def buildTournamentLineupSubmissionView(submission: StageLineupSubmission): TournamentLineupSubmissionView =
    buildTournamentLineupSubmissionView(
      submission,
      loadClubsById(Vector(submission.clubId)),
      loadPlayersById(Vector(submission.submittedBy))
    )

  private def buildTournamentLineupSubmissionView(
      submission: StageLineupSubmission,
      clubsById: Map[ClubId, Club],
      playersById: Map[PlayerId, Player]
  ): TournamentLineupSubmissionView =
    TournamentLineupSubmissionView(
      submissionId = submission.id,
      clubId = submission.clubId,
      clubName = clubsById.get(submission.clubId).map(_.name).getOrElse(submission.clubId.value),
      submittedBy = submission.submittedBy,
      submittedByDisplayName = playersById.get(submission.submittedBy).map(_.nickname),
      submittedAt = submission.submittedAt,
      activePlayerIds = submission.seats.filterNot(_.reserve).map(_.playerId),
      reservePlayerIds = submission.seats.filter(_.reserve).map(_.playerId),
      note = submission.note
    )

  def buildTournamentOperationsStageView(stage: TournamentStage): TournamentOperationsStageView =
    buildTournamentOperationsStageView(
      stage,
      loadClubsById(stage.lineupSubmissions.map(_.clubId)),
      loadPlayersById(stage.lineupSubmissions.map(_.submittedBy))
    )

  private def buildTournamentOperationsStageView(
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
      lineupSubmissions = stage.lineupSubmissions
        .sortBy(_.submittedAt)
        .map(submission => buildTournamentLineupSubmissionView(submission, clubsById, playersById))
    )

  def buildTournamentDetailView(tournament: Tournament): TournamentDetailView =
    val tournamentClubIds = tournamentRelatedClubIds(tournament)
    val clubsById = loadClubsById(tournamentClubIds)
    val participantIds = tournamentParticipantIds(tournament, clubsById)
    val playerIdsForLookup = (
      tournament.participatingClubs.distinct.flatMap(clubId => clubsById.get(clubId).toVector.flatMap(_.members)) ++
        participantIds ++
        tournament.stages.flatMap(_.lineupSubmissions.map(_.submittedBy))
    ).distinct
    val playersById = loadPlayersById(playerIdsForLookup)
    val participatingClubs = tournament.participatingClubs.distinct.flatMap { clubId =>
      clubsById.get(clubId).map { club =>
        TournamentParticipantClubView(
          clubId = club.id,
          clubName = club.name,
          memberCount = club.members.size,
          activeMemberCount = club.members.count(playerId =>
            playersById.get(playerId).exists(_.status == PlayerStatus.Active)
          )
        )
      }
    }.sortBy(club => (club.clubName, club.clubId.value))

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
    }.sortBy(player => (player.nickname, player.playerId.value))

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
        clubIds = whitelistedClubIds,
        playerIds = whitelistedPlayerIds
      ),
      stages = tournament.stages.sortBy(_.order).map(stage =>
        buildTournamentOperationsStageView(stage, clubsById, playersById)
      )
    )

  def buildTournamentDetailView(tournamentId: TournamentId): Option[TournamentDetailView] =
    app.tournamentRepository.findById(tournamentId).map(buildTournamentDetailView)

  def buildTournamentMutationView(
      tournamentId: TournamentId,
      scheduledTables: Vector[Table] = Vector.empty
  ): Option[TournamentMutationView] =
    buildTournamentDetailView(tournamentId).map(detail =>
      TournamentMutationView(
        tournament = detail,
        scheduledTables = scheduledTables.sortBy(table => (table.stageRoundNumber, table.tableNo, table.id.value))
      )
    )

  def buildClubTournamentParticipationView(
      clubId: ClubId,
      tournament: Tournament,
      viewer: AccessPrincipal
  ): Option[ClubTournamentParticipationView] =
    val club = app.clubRepository.findById(clubId)
    val clubVisibleToViewer =
      club.exists(currentClub => canManageClubTournamentParticipation(viewer, currentClub))
    val isWhitelisted = tournament.whitelist.exists(_.clubId.contains(clubId))
    val isParticipating = tournament.participatingClubs.contains(clubId)
    if !isWhitelisted && !isParticipating then None
    else
      val stageName = tournament.stages
        .sortBy(_.order)
        .find(stage => stage.status != StageStatus.Completed && stage.status != StageStatus.Archived)
        .orElse(tournament.stages.sortBy(_.order).lastOption)
        .map(_.name)
      Some(
        ClubTournamentParticipationView(
          clubId = clubId,
          tournamentId = tournament.id,
          name = tournament.name,
          status = tournament.status,
          clubParticipationStatus =
            if isParticipating then ClubTournamentParticipationStatus.Participating
            else ClubTournamentParticipationStatus.Invited,
          stageName = stageName,
          startsAt = tournament.startsAt,
          endsAt = tournament.endsAt,
          canViewDetail = tournament.status != TournamentStatus.Draft || clubVisibleToViewer,
          canSubmitLineup =
            clubVisibleToViewer &&
              tournament.status != TournamentStatus.Draft &&
              tournament.status != TournamentStatus.Cancelled &&
              tournament.status != TournamentStatus.Archived &&
              (isWhitelisted || isParticipating),
          canDecline =
            clubVisibleToViewer &&
              tournament.status != TournamentStatus.Completed &&
              tournament.status != TournamentStatus.Cancelled &&
              tournament.status != TournamentStatus.Archived
        )
      )

  def buildPublicTournamentSummaryView(tournament: Tournament): PublicTournamentSummaryView =
    buildPublicTournamentSummaryView(
      tournament,
      loadClubsById(tournamentRelatedClubIds(tournament))
    )

  def buildPublicTournamentSummaryViews(tournaments: Vector[Tournament]): Vector[PublicTournamentSummaryView] =
    val clubsById = loadClubsById(tournaments.flatMap(tournamentRelatedClubIds))
    tournaments.map(tournament => buildPublicTournamentSummaryView(tournament, clubsById))

  private def buildPublicTournamentSummaryView(
      tournament: Tournament,
      clubsById: Map[ClubId, Club]
  ): PublicTournamentSummaryView =
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

  def buildPublicTournamentDetailView(tournamentId: TournamentId): Option[PublicTournamentDetailView] =
    app.tournamentRepository.findById(tournamentId)
      .filter(_.status != TournamentStatus.Draft)
      .map { tournament =>
        val clubsById = loadClubsById(tournamentRelatedClubIds(tournament))
        val tablesByStage = app.tableRepository.findByTournamentIds(Vector(tournament.id))
          .groupBy(_.stageId)
          .withDefaultValue(Vector.empty)
        val stages = tournament.stages
          .sortBy(_.order)
          .map { stage =>
            val tables = tablesByStage(stage.id)
            val bracket =
              if stage.format == StageFormat.Knockout || stage.format == StageFormat.Finals then
                Some(app.tournamentService.stageKnockoutBracket(tournament.id, stage.id))
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
              standings = Some(app.tournamentService.stageStandings(tournament.id, stage.id)),
              bracket = bracket
            )
          }

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
          stages = stages
        )
      }

  def tournamentParticipantIds(tournament: Tournament): Vector[PlayerId] =
    tournamentParticipantIds(
      tournament,
      loadClubsById(tournamentRelatedClubIds(tournament))
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

  protected def tournamentRelatedClubIds(tournament: Tournament): Vector[ClubId] =
    (tournament.participatingClubs ++ tournament.whitelist.flatMap(_.clubId)).distinct
