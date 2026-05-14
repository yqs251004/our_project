package riichinexus.microservices.club.api

import riichinexus.domain.model.*
import riichinexus.domain.service.{AuthorizationFailure, AuthorizationService}
import riichinexus.microservices.club.api.responses.*
import riichinexus.microservices.publicquery.api.responses.*
import riichinexus.microservices.club.tables.ClubTables

final class ClubViewAssembler(
    clubTables: ClubTables,
    authorizationService: AuthorizationService
):
  private def loadPlayersById(playerIds: Iterable[PlayerId]): Map[PlayerId, Player] =
    clubTables.findPlayers(playerIds)
      .map(player => player.id -> player)
      .toMap

  def clubApplicationsOpen(club: Club): Boolean =
    club.dissolvedAt.isEmpty && club.recruitmentPolicy.applicationsOpen

  def clubApplicationPolicy(club: Club): ClubApplicationPolicyView =
    ClubApplicationPolicyView(
      applicationsOpen = clubApplicationsOpen(club),
      requirementsText =
        if clubApplicationsOpen(club) then club.recruitmentPolicy.requirementsText else None,
      expectedReviewSlaHours =
        if clubApplicationsOpen(club) then club.recruitmentPolicy.expectedReviewSlaHours else None,
      pendingApplicationCount = club.membershipApplications.count(_.isPending)
    )

  def canManageClubApplications(actor: AccessPrincipal, club: Club): Boolean =
    actor.isSuperAdmin || actor.playerId.exists(playerId =>
      club.admins.contains(playerId) || club.hasPrivilege(playerId, ClubPrivilege.ApproveRoster)
    )

  def canManageClubTournamentParticipation(actor: AccessPrincipal, club: Club): Boolean =
    actor.isSuperAdmin ||
      authorizationService.can(actor, Permission.SubmitTournamentLineup, clubId = Some(club.id)) ||
      actor.playerId.exists(playerId =>
        club.members.contains(playerId) && club.hasPrivilege(playerId, ClubPrivilege.PriorityLineup)
      )

  def buildClubTournamentParticipationView(
      clubId: ClubId,
      tournament: Tournament,
      viewer: AccessPrincipal
  ): Option[ClubTournamentParticipationView] =
    val club = clubTables.findClub(clubId)
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

  def requireClubApplicationManager(actor: AccessPrincipal, club: Club): Unit =
    if !canManageClubApplications(actor, club) then
      throw AuthorizationFailure(s"${actor.displayName} cannot manage membership applications for club ${club.id.value}")

  def ownsClubApplication(actor: AccessPrincipal, application: ClubMembershipApplication): Boolean =
    val ownedByGuest = actor.isGuest && application.applicantUserId.contains(s"guest:${actor.principalId}")
    val ownedByRegisteredPlayer =
      actor.playerId.flatMap(clubTables.findPlayer).exists(player =>
        application.applicantUserId.contains(player.userId)
      )
    ownedByGuest || ownedByRegisteredPlayer

  def canWithdrawClubApplication(actor: AccessPrincipal, application: ClubMembershipApplication): Boolean =
    actor.isSuperAdmin || ownsClubApplication(actor, application)

  def requireClubApplicationViewer(actor: AccessPrincipal, club: Club, application: ClubMembershipApplication): Unit =
    if !canManageClubApplications(actor, club) && !canWithdrawClubApplication(actor, application) then
      throw AuthorizationFailure(s"${actor.displayName} cannot view membership application ${application.id.value}")

  def buildClubMembershipApplicationView(
      club: Club,
      application: ClubMembershipApplication,
      actor: AccessPrincipal
  ): ClubMembershipApplicationView =
    val applicantPlayer = application.applicantUserId.flatMap(clubTables.findPlayerByUserId)
    ClubMembershipApplicationView(
      applicationId = application.id,
      clubId = club.id,
      clubName = club.name,
      applicant = ClubMembershipApplicantView(
        playerId = applicantPlayer.map(_.id),
        applicantUserId = application.applicantUserId,
        displayName = application.displayName,
        playerStatus = applicantPlayer.map(_.status),
        currentRank = applicantPlayer.map(_.currentRank),
        elo = applicantPlayer.map(_.elo),
        clubIds = applicantPlayer.map(_.boundClubIds).getOrElse(Vector.empty)
      ),
      submittedAt = application.submittedAt,
      message = application.message,
      status = application.status,
      reviewedBy = application.reviewedBy,
      reviewedByDisplayName = application.reviewedBy.flatMap(playerId => clubTables.findPlayer(playerId).map(_.nickname)),
      reviewedAt = application.reviewedAt,
      reviewNote = application.reviewNote,
      withdrawnByPrincipalId = application.withdrawnByPrincipalId,
      canReview = application.isPending && canManageClubApplications(actor, club),
      canWithdraw = application.isPending && canWithdrawClubApplication(actor, application)
    )

  def buildPublicClubDetailView(clubId: ClubId): Option[PublicClubDetailView] =
    clubTables.findClub(clubId)
      .filter(_.dissolvedAt.isEmpty)
      .map { club =>
        val recentRecords = clubTables.listRecentMatchRecordsByClub(club.id, limit = 8)
        val lineupPlayerIds = latestClubLineupPlayerIds(club).getOrElse(club.members)
        val recentTournamentIds = recentRecords.map(_.tournamentId).distinct
        val tournamentsById = clubTables.findTournaments(recentTournamentIds)
          .map(tournament => tournament.id -> tournament)
          .toMap
        val playersById = loadPlayersById(
          (club.members ++ lineupPlayerIds ++ recentRecords.flatMap(_.seatResults.map(_.playerId))).distinct
        )
        val tournamentCache = scala.collection.mutable.Map.empty[TournamentId, Option[Tournament]]
        def tournamentFor(id: TournamentId): Option[Tournament] =
          tournamentCache.getOrElseUpdate(id, tournamentsById.get(id).orElse(clubTables.findTournament(id)))
        def nicknameFor(id: PlayerId): String =
          playersById.get(id).map(_.nickname).getOrElse(id.value)

        val currentLineup = lineupPlayerIds
          .flatMap(playerId => playersById.get(playerId))
          .sortBy(player => (-player.elo, player.nickname, player.id.value))
          .map { player =>
            val privilegeSnapshot = club.memberPrivilegeSnapshot(player.id)
            PublicClubLineupMemberView(
              playerId = player.id,
              nickname = player.nickname,
              elo = player.elo,
              currentRank = player.currentRank,
              status = player.status,
              isAdmin = club.admins.contains(player.id),
              internalTitle = privilegeSnapshot.flatMap(_.internalTitle),
              privileges = privilegeSnapshot.map(_.privileges).getOrElse(Vector.empty)
            )
          }

        val recentMatches = recentRecords.map { record =>
          val tournamentName = tournamentFor(record.tournamentId).map(_.name).getOrElse(record.tournamentId.value)
          val stageName = tournamentFor(record.tournamentId)
            .flatMap(_.stages.find(_.id == record.stageId))
            .map(_.name)
            .getOrElse(record.stageId.value)
          PublicClubRecentMatchView(
            matchRecordId = record.id,
            tournamentId = record.tournamentId,
            tournamentName = tournamentName,
            stageId = record.stageId,
            stageName = stageName,
            tableId = record.tableId,
            generatedAt = record.generatedAt,
            seats = record.seatResults
              .sortBy(_.placement)
              .map { result =>
                PublicClubRecentMatchSeatView(
                  playerId = result.playerId,
                  nickname = nicknameFor(result.playerId),
                  clubId = result.clubId,
                  seat = result.seat,
                  placement = result.placement,
                  scoreDelta = result.scoreDelta,
                  finalPoints = result.finalPoints
                )
              }
          )
        }

        PublicClubDetailView(
          clubId = club.id,
          name = club.name,
          memberCount = club.members.size,
          activeMemberCount = club.members.count(playerId =>
            playersById.get(playerId).exists(_.status == PlayerStatus.Active)
          ),
          adminCount = club.admins.size,
          powerRating = club.powerRating,
          totalPoints = club.totalPoints,
          treasuryBalance = club.treasuryBalance,
          pointPool = club.pointPool,
          relations = club.relations,
          honors = club.honors.sortBy(honor => (honor.achievedAt, honor.title)).reverse,
          applicationPolicy = clubApplicationPolicy(club),
          currentLineup = currentLineup,
          recentMatches = recentMatches
        )
      }

  def latestClubLineupPlayerIds(club: Club): Option[Vector[PlayerId]] =
    clubTables.listTournamentsByClub(club.id)
      .filter(_.status != TournamentStatus.Draft)
      .flatMap(_.stages)
      .flatMap(_.lineupSubmissions)
      .filter(_.clubId == club.id)
      .sortBy(submission => (submission.submittedAt, submission.id.value))
      .lastOption
      .map(_.activePlayerIds)
