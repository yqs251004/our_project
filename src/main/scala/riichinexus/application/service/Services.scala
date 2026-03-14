package riichinexus.application.service

import java.time.Instant
import java.util.NoSuchElementException

import riichinexus.application.ports.*
import riichinexus.domain.event.*
import riichinexus.domain.model.*
import riichinexus.domain.service.*

final class PlayerApplicationService(
    playerRepository: PlayerRepository,
    transactionManager: TransactionManager = NoOpTransactionManager
):
  def registerPlayer(
      userId: String,
      nickname: String,
      rank: RankSnapshot,
      registeredAt: Instant = Instant.now(),
      initialElo: Int = 1500
  ): Player =
    transactionManager.inTransaction {
      val player = playerRepository.findByUserId(userId) match
        case Some(existing) =>
          existing.copy(
            nickname = nickname,
            currentRank = rank
          )
        case None =>
          Player(
            id = IdGenerator.playerId(),
            userId = userId,
            nickname = nickname,
            registeredAt = registeredAt,
            currentRank = rank,
            elo = initialElo,
            roleGrants = Vector(RoleGrant.registered(registeredAt))
          )

      playerRepository.save(player)
    }

final class PublicQueryService(
    tournamentRepository: TournamentRepository,
    tableRepository: TableRepository,
    playerRepository: PlayerRepository,
    clubRepository: ClubRepository,
    authorizationService: AuthorizationService = NoOpAuthorizationService
):
  private val guestPrincipal = AccessPrincipal.guest()

  def publicSchedules(): Vector[PublicScheduleView] =
    authorizationService.requirePermission(guestPrincipal, Permission.ViewPublicSchedule)

    tournamentRepository.findAll().filter(_.status != TournamentStatus.Draft).flatMap { tournament =>
      tournament.stages.map { stage =>
        PublicScheduleView(
          tournamentId = tournament.id,
          tournamentName = tournament.name,
          tournamentStatus = tournament.status,
          stageId = stage.id,
          stageName = stage.name,
          stageStatus = stage.status,
          startsAt = tournament.startsAt,
          endsAt = tournament.endsAt,
          tableCount = tableRepository.findByTournamentAndStage(tournament.id, stage.id).size
        )
      }
    }

  def publicClubDirectory(): Vector[PublicClubDirectoryEntry] =
    authorizationService.requirePermission(guestPrincipal, Permission.ViewClubDirectory)

    clubRepository.findActive().sortBy(_.name).map { club =>
      PublicClubDirectoryEntry(
        clubId = club.id,
        name = club.name,
        memberCount = club.members.size,
        adminCount = club.admins.size,
        powerRating = round2(club.powerRating),
        totalPoints = club.totalPoints,
        relations = club.relations
      )
    }

  def publicPlayerLeaderboard(limit: Int = 100): Vector[PlayerLeaderboardEntry] =
    authorizationService.requirePermission(guestPrincipal, Permission.ViewPublicLeaderboard)

    playerRepository.findAll()
      .sortBy(player => (-player.elo, player.nickname))
      .take(limit)
      .map { player =>
        PlayerLeaderboardEntry(
          playerId = player.id,
          nickname = player.nickname,
          elo = player.elo,
          clubIds = player.boundClubIds,
          status = player.status
        )
      }

  def publicClubLeaderboard(limit: Int = 100): Vector[ClubLeaderboardEntry] =
    authorizationService.requirePermission(guestPrincipal, Permission.ViewPublicLeaderboard)

    clubRepository.findActive()
      .sortBy(club => (-club.powerRating, -club.totalPoints, club.name))
      .take(limit)
      .map { club =>
        ClubLeaderboardEntry(
          clubId = club.id,
          name = club.name,
          powerRating = round2(club.powerRating),
          totalPoints = club.totalPoints,
          memberCount = club.members.size
        )
      }

  private def round2(value: Double): Double =
    BigDecimal(value).setScale(2, BigDecimal.RoundingMode.HALF_UP).toDouble

final class ClubApplicationService(
    clubRepository: ClubRepository,
    playerRepository: PlayerRepository,
    transactionManager: TransactionManager = NoOpTransactionManager,
    authorizationService: AuthorizationService = NoOpAuthorizationService
):
  def createClub(
      name: String,
      creatorId: PlayerId,
      createdAt: Instant = Instant.now(),
      actor: AccessPrincipal = AccessPrincipal.system
  ): Club =
    transactionManager.inTransaction {
      val normalizedName = name.trim
      require(normalizedName.nonEmpty, "Club name cannot be empty")

      val creator = playerRepository
        .findById(creatorId)
        .getOrElse(throw NoSuchElementException(s"Player ${creatorId.value} was not found"))
      requireActivePlayer(creator, s"Player ${creatorId.value} cannot create a club")

      if !actor.isSuperAdmin && actor.playerId.exists(_ != creatorId) then
        throw AuthorizationFailure("Only the creator or a super admin can create the club")

      val club = clubRepository.findByName(normalizedName) match
        case Some(existing) =>
          ensureClubActive(existing)
          existing
            .addMember(creatorId)
            .grantAdmin(creatorId)
        case None =>
          Club(
            id = IdGenerator.clubId(),
            name = normalizedName,
            creator = creatorId,
            createdAt = createdAt,
            members = Vector(creatorId),
            admins = Vector(creatorId)
          )

      val updatedCreator = creator
        .joinClub(club.id)
        .grantRole(RoleGrant.clubAdmin(club.id, createdAt, actor.playerId))

      playerRepository.save(updatedCreator)
      clubRepository.save(club)
    }

  def addMember(
      clubId: ClubId,
      playerId: PlayerId,
      actor: AccessPrincipal = AccessPrincipal.system
  ): Option[Club] =
    transactionManager.inTransaction {
      for
        club <- clubRepository.findById(clubId)
        player <- playerRepository.findById(playerId)
      yield
        ensureClubActive(club)
        requireActivePlayer(player, s"Player ${playerId.value} cannot join club ${clubId.value}")
        authorizationService.requirePermission(
          actor,
          Permission.ManageClubMembership,
          clubId = Some(clubId)
        )

        playerRepository.save(player.joinClub(clubId))
        clubRepository.save(club.addMember(playerId))
    }

  def applyForMembership(
      clubId: ClubId,
      applicantUserId: Option[String],
      displayName: String,
      message: Option[String] = None,
      submittedAt: Instant = Instant.now(),
      actor: AccessPrincipal = AccessPrincipal.guest()
  ): Option[ClubMembershipApplication] =
    transactionManager.inTransaction {
      authorizationService.requirePermission(actor, Permission.SubmitClubApplication)

      clubRepository.findById(clubId).map { club =>
        ensureClubActive(club)

        applicantUserId.foreach { userId =>
          if club.membershipApplications.exists(application =>
              application.applicantUserId.contains(userId) && application.isPending
            )
          then
            throw IllegalArgumentException(
              s"User $userId already has a pending application for club ${clubId.value}"
            )

          playerRepository.findByUserId(userId).foreach { existingPlayer =>
            if existingPlayer.boundClubIds.contains(clubId) then
              throw IllegalArgumentException(
                s"Player ${existingPlayer.id.value} is already a member of club ${clubId.value}"
              )
          }
        }

        val application = ClubMembershipApplication(
          id = IdGenerator.membershipApplicationId(),
          applicantUserId = applicantUserId,
          displayName = displayName,
          submittedAt = submittedAt,
          message = message
        )

        clubRepository.save(club.submitApplication(application))
        application
      }
    }

  def approveMembershipApplication(
      clubId: ClubId,
      applicationId: MembershipApplicationId,
      playerId: PlayerId,
      actor: AccessPrincipal,
      approvedAt: Instant = Instant.now(),
      note: Option[String] = None
  ): Option[Club] =
    transactionManager.inTransaction {
      for
        club <- clubRepository.findById(clubId)
        player <- playerRepository.findById(playerId)
      yield
        ensureClubActive(club)
        requireActivePlayer(player, s"Player ${playerId.value} cannot be approved into a club")
        authorizationService.requirePermission(
          actor,
          Permission.ManageClubMembership,
          clubId = Some(clubId)
        )

        val application = club
          .findApplication(applicationId)
          .getOrElse(
            throw NoSuchElementException(
              s"Membership application ${applicationId.value} was not found in club ${clubId.value}"
            )
          )

        if !application.isPending then
          throw IllegalArgumentException(
            s"Membership application ${applicationId.value} has already been reviewed"
          )

        if club.members.contains(playerId) then
          throw IllegalArgumentException(
            s"Player ${playerId.value} is already a member of club ${clubId.value}"
          )

        if application.applicantUserId.exists(_ != player.userId) then
          throw IllegalArgumentException(
            s"Membership application ${applicationId.value} does not belong to player ${playerId.value}"
          )

        val reviewer = actor.playerId.getOrElse(club.creator)
        val updatedClub = club
          .reviewApplication(applicationId, _.approve(reviewer, approvedAt, note))
          .addMember(playerId)

        playerRepository.save(player.joinClub(clubId))
        clubRepository.save(updatedClub)
    }

  def assignAdmin(
      clubId: ClubId,
      playerId: PlayerId,
      actor: AccessPrincipal,
      grantedAt: Instant = Instant.now()
  ): Option[Club] =
    transactionManager.inTransaction {
      for
        club <- clubRepository.findById(clubId)
        player <- playerRepository.findById(playerId)
      yield
        ensureClubActive(club)
        requireActivePlayer(player, s"Player ${playerId.value} cannot be granted club admin")
        requireClubMember(club, playerId, "assign club admin")
        authorizationService.requirePermission(
          actor,
          Permission.AssignClubAdmin,
          clubId = Some(clubId)
        )

        playerRepository.save(
          player.grantRole(RoleGrant.clubAdmin(clubId, grantedAt, actor.playerId))
        )
        clubRepository.save(club.grantAdmin(playerId))
    }

  def setInternalTitle(
      clubId: ClubId,
      playerId: PlayerId,
      title: String,
      actor: AccessPrincipal,
      assignedAt: Instant = Instant.now(),
      note: Option[String] = None
  ): Option[Club] =
    transactionManager.inTransaction {
      for
        club <- clubRepository.findById(clubId)
        player <- playerRepository.findById(playerId)
      yield
        ensureClubActive(club)
        requireActivePlayer(player, s"Player ${playerId.value} cannot receive club title")
        requireClubMember(club, playerId, "set internal title")
        authorizationService.requirePermission(
          actor,
          Permission.SetClubTitle,
          clubId = Some(clubId)
        )

        val assignedBy = actor.playerId.getOrElse(club.creator)
        clubRepository.save(
          club.setInternalTitle(
            ClubTitleAssignment(
              playerId = playerId,
              title = title,
              assignedBy = assignedBy,
              assignedAt = assignedAt,
              note = note
            )
          )
        )
    }

  def updateRelation(
      clubId: ClubId,
      relation: ClubRelation,
      actor: AccessPrincipal
  ): Option[Club] =
    transactionManager.inTransaction {
      clubRepository.findById(clubId).map { club =>
        ensureClubActive(club)
        authorizationService.requirePermission(
          actor,
          Permission.SetClubTitle,
          clubId = Some(clubId)
        )

        if relation.targetClubId == clubId then
          throw IllegalArgumentException("A club cannot define a relation to itself")

        clubRepository
          .findById(relation.targetClubId)
          .map(ensureClubActive)
          .getOrElse(
            throw NoSuchElementException(s"Club ${relation.targetClubId.value} was not found")
          )

        if relation.relation == ClubRelationKind.Neutral then
          clubRepository.save(club.removeRelation(relation.targetClubId))
        else clubRepository.save(club.upsertRelation(relation))
      }
    }

  private def ensureClubActive(club: Club): Unit =
    if club.dissolvedAt.nonEmpty then
      throw IllegalArgumentException(s"Club ${club.id.value} has already been dissolved")

  private def requireActivePlayer(player: Player, context: String): Unit =
    if player.status != PlayerStatus.Active then
      throw IllegalArgumentException(context)

  private def requireClubMember(club: Club, playerId: PlayerId, action: String): Unit =
    if !club.members.contains(playerId) then
      throw IllegalArgumentException(
        s"Player ${playerId.value} must be a club member to $action in club ${club.id.value}"
      )

final class TournamentApplicationService(
    tournamentRepository: TournamentRepository,
    playerRepository: PlayerRepository,
    clubRepository: ClubRepository,
    tableRepository: TableRepository,
    matchRecordRepository: MatchRecordRepository,
    seatingPolicy: SeatingPolicy,
    tournamentRuleEngine: TournamentRuleEngine,
    knockoutStageCoordinator: KnockoutStageCoordinator,
    transactionManager: TransactionManager = NoOpTransactionManager,
    authorizationService: AuthorizationService = NoOpAuthorizationService
):
  def createTournament(
      name: String,
      organizer: String,
      startsAt: Instant,
      endsAt: Instant,
      stages: Vector[TournamentStage],
      adminId: Option[PlayerId] = None,
      actor: AccessPrincipal = AccessPrincipal.system
  ): Tournament =
    transactionManager.inTransaction {
      require(name.trim.nonEmpty, "Tournament name cannot be empty")
      require(organizer.trim.nonEmpty, "Tournament organizer cannot be empty")
      require(startsAt.isBefore(endsAt), "Tournament start time must be earlier than end time")

      val normalizedStages = stages.map(normalizeStage).sortBy(_.order)
      requireUniqueStageConfiguration(normalizedStages)

      adminId.foreach { targetAdminId =>
        val adminPlayer = playerRepository
          .findById(targetAdminId)
          .getOrElse(throw NoSuchElementException(s"Player ${targetAdminId.value} was not found"))
        requireActivePlayer(adminPlayer, s"Player ${targetAdminId.value} cannot administer tournaments")
      }

      val tournament = tournamentRepository.findByNameAndOrganizer(name, organizer) match
        case Some(existing) =>
          existing.copy(
            startsAt = startsAt,
            endsAt = endsAt,
            stages = normalizedStages
          )
        case None =>
          Tournament(
            id = IdGenerator.tournamentId(),
            name = name,
            organizer = organizer,
            startsAt = startsAt,
            endsAt = endsAt,
            admins = adminId.toVector,
            stages = normalizedStages
          )

      adminId.foreach { targetAdminId =>
        playerRepository.findById(targetAdminId).foreach { adminPlayer =>
          playerRepository.save(
            adminPlayer.grantRole(
              RoleGrant.tournamentAdmin(tournament.id, startsAt, actor.playerId)
            )
          )
        }
      }

      tournamentRepository.save(
        adminId.fold(tournament)(tournament.assignAdmin)
      )
    }

  def registerPlayer(tournamentId: TournamentId, playerId: PlayerId): Option[Tournament] =
    transactionManager.inTransaction {
      playerRepository
        .findById(playerId)
        .map { player =>
          requireActivePlayer(player, s"Player ${playerId.value} cannot enter tournament ${tournamentId.value}")
        }
        .getOrElse(throw NoSuchElementException(s"Player ${playerId.value} was not found"))

      tournamentRepository.findById(tournamentId).map { tournament =>
        tournamentRepository.save(tournament.registerPlayer(playerId))
      }
    }

  def registerClub(tournamentId: TournamentId, clubId: ClubId): Option[Tournament] =
    transactionManager.inTransaction {
      clubRepository
        .findById(clubId)
        .map(ensureClubActive)
        .getOrElse(throw NoSuchElementException(s"Club ${clubId.value} was not found"))

      tournamentRepository.findById(tournamentId).map { tournament =>
        tournamentRepository.save(tournament.registerClub(clubId))
      }
    }

  def whitelistPlayer(tournamentId: TournamentId, playerId: PlayerId): Option[Tournament] =
    transactionManager.inTransaction {
      playerRepository
        .findById(playerId)
        .map { player =>
          requireActivePlayer(player, s"Player ${playerId.value} cannot be whitelisted")
        }
        .getOrElse(throw NoSuchElementException(s"Player ${playerId.value} was not found"))

      tournamentRepository.findById(tournamentId).map { tournament =>
        tournamentRepository.save(tournament.whitelistPlayer(playerId))
      }
    }

  def whitelistClub(tournamentId: TournamentId, clubId: ClubId): Option[Tournament] =
    transactionManager.inTransaction {
      clubRepository
        .findById(clubId)
        .map(ensureClubActive)
        .getOrElse(throw NoSuchElementException(s"Club ${clubId.value} was not found"))

      tournamentRepository.findById(tournamentId).map { tournament =>
        tournamentRepository.save(tournament.whitelistClub(clubId))
      }
    }

  def publishTournament(tournamentId: TournamentId): Option[Tournament] =
    transactionManager.inTransaction {
      tournamentRepository.findById(tournamentId).map { tournament =>
        if tournament.stages.isEmpty then
          throw IllegalArgumentException(
            s"Tournament ${tournamentId.value} cannot be published without stages"
          )
        tournamentRepository.save(tournament.publish)
      }
    }

  def startTournament(tournamentId: TournamentId): Option[Tournament] =
    transactionManager.inTransaction {
      tournamentRepository.findById(tournamentId).map { tournament =>
        if tournament.participatingPlayers.isEmpty && tournament.participatingClubs.isEmpty then
          throw IllegalArgumentException(
            s"Tournament ${tournamentId.value} cannot start without participants"
          )
        tournamentRepository.save(tournament.start)
      }
    }

  def assignTournamentAdmin(
      tournamentId: TournamentId,
      playerId: PlayerId,
      actor: AccessPrincipal,
      grantedAt: Instant = Instant.now()
  ): Option[Tournament] =
    transactionManager.inTransaction {
      for
        tournament <- tournamentRepository.findById(tournamentId)
        player <- playerRepository.findById(playerId)
      yield
        requireActivePlayer(player, s"Player ${playerId.value} cannot be granted tournament admin")
        authorizationService.requirePermission(
          actor,
          Permission.AssignTournamentAdmin,
          tournamentId = Some(tournamentId)
        )

        playerRepository.save(
          player.grantRole(
            RoleGrant.tournamentAdmin(tournamentId, grantedAt, actor.playerId)
          )
        )
        tournamentRepository.save(tournament.assignAdmin(playerId))
    }

  def addStage(
      tournamentId: TournamentId,
      stage: TournamentStage,
      actor: AccessPrincipal
  ): Option[Tournament] =
    transactionManager.inTransaction {
      tournamentRepository.findById(tournamentId).map { tournament =>
        if tournament.status == TournamentStatus.Completed || tournament.status == TournamentStatus.Archived then
          throw IllegalArgumentException(
            s"Cannot add stages to tournament ${tournamentId.value} in status ${tournament.status}"
          )

        authorizationService.requirePermission(
          actor,
          Permission.ManageTournamentStages,
          tournamentId = Some(tournamentId)
        )

        tournamentRepository.save(tournament.addStage(normalizeStage(stage)))
      }
    }

  def configureStageRules(
      tournamentId: TournamentId,
      stageId: TournamentStageId,
      advancementRule: AdvancementRule,
      swissRule: Option[SwissRuleConfig],
      knockoutRule: Option[KnockoutRuleConfig],
      schedulingPoolSize: Int,
      actor: AccessPrincipal
  ): Option[Tournament] =
    transactionManager.inTransaction {
      tournamentRepository.findById(tournamentId).map { tournament =>
        requireStage(tournament, stageId)
        authorizationService.requirePermission(
          actor,
          Permission.ConfigureTournamentRules,
          tournamentId = Some(tournamentId)
        )

        tournamentRepository.save(
          tournament.updateStage(stageId, _.withRules(advancementRule, swissRule, knockoutRule, schedulingPoolSize))
        )
      }
    }

  def submitLineup(
      tournamentId: TournamentId,
      stageId: TournamentStageId,
      submission: StageLineupSubmission,
      actor: AccessPrincipal
  ): Option[Tournament] =
    transactionManager.inTransaction {
      tournamentRepository.findById(tournamentId).map { tournament =>
        requireStage(tournament, stageId)
        authorizationService.requirePermission(
          actor,
          Permission.SubmitTournamentLineup,
          clubId = Some(submission.clubId)
        )

        if !actor.isSuperAdmin && actor.playerId.exists(_ != submission.submittedBy) then
          throw AuthorizationFailure("Lineup submitter must match the acting principal")

        val isClubRegistered =
          tournament.participatingClubs.contains(submission.clubId) ||
            tournament.whitelist.exists(_.clubId.contains(submission.clubId))

        if !isClubRegistered then
          throw IllegalArgumentException(
            s"Club ${submission.clubId.value} is not whitelisted for tournament ${tournamentId.value}"
          )

        val club = clubRepository
          .findById(submission.clubId)
          .getOrElse(throw NoSuchElementException(s"Club ${submission.clubId.value} was not found"))
        ensureClubActive(club)

        submission.activePlayerIds.foreach { playerId =>
          if !club.members.contains(playerId) then
            throw IllegalArgumentException(
              s"Player ${playerId.value} is not a member of club ${submission.clubId.value}"
            )

          val player = playerRepository
            .findById(playerId)
            .getOrElse(throw NoSuchElementException(s"Player ${playerId.value} was not found"))
          requireActivePlayer(player, s"Player ${playerId.value} cannot be submitted to tournament lineups")
        }

        tournamentRepository.save(
          tournament.updateStage(stageId, _.submitLineup(submission))
        )
      }
    }

  def scheduleStageTables(
      tournamentId: TournamentId,
      stageId: TournamentStageId,
      actor: AccessPrincipal = AccessPrincipal.system
  ): Vector[Table] =
    transactionManager.inTransaction {
      authorizationService.requirePermission(
        actor,
        Permission.ManageTournamentStages,
        tournamentId = Some(tournamentId)
      )

      val existingTables = tableRepository.findByTournamentAndStage(tournamentId, stageId)
      if existingTables.nonEmpty then existingTables
      else
        val tournament = tournamentRepository
          .findById(tournamentId)
          .getOrElse(throw IllegalArgumentException(s"Tournament ${tournamentId.value} was not found"))

        val stage = tournament.stages
          .find(_.id == stageId)
          .getOrElse(throw IllegalArgumentException(s"Stage ${stageId.value} was not found"))

        if tournament.status == TournamentStatus.Draft then
          throw IllegalArgumentException(
            s"Tournament ${tournamentId.value} must be published before scheduling tables"
          )

        val isKnockoutStage =
          stage.format == StageFormat.Knockout ||
            stage.format == StageFormat.Finals ||
            stage.advancementRule.ruleType == AdvancementRuleType.KnockoutElimination

        if isKnockoutStage then
          knockoutStageCoordinator.materializeUnlockedTables(tournamentId, stageId)
        else
          val tournamentPlayers = resolveParticipants(tournament, stage)
          if tournamentPlayers.size < 4 then
            throw IllegalArgumentException(
              s"Stage ${stageId.value} needs at least four active players before scheduling"
            )
          val plannedTables = seatingPolicy.assignTables(tournamentPlayers, stage)

          val savedTables = plannedTables.map { planned =>
            tableRepository.save(
              Table(
                id = IdGenerator.tableId(),
                tableNo = planned.tableNo,
                tournamentId = tournamentId,
                stageId = stageId,
                seats = planned.seats
              )
            )
          }

          tournamentRepository.save(
            tournament
              .activateStage(stageId)
              .markScheduled
              .updateStage(stageId, _.registerScheduledTables(savedTables.map(_.id)))
          )

          savedTables
    }

  def stageStandings(
      tournamentId: TournamentId,
      stageId: TournamentStageId,
      at: Instant = Instant.now()
  ): StageRankingSnapshot =
    val tournament = tournamentRepository
      .findById(tournamentId)
      .getOrElse(throw NoSuchElementException(s"Tournament ${tournamentId.value} was not found"))
    val stage = requireStage(tournament, stageId)
    val records = stageRecords(tournamentId, stageId)
    val participants = resolveParticipants(tournament, stage).map(_.id)
    tournamentRuleEngine.buildStageRanking(tournament, stage, participants, records, at)

  def stageAdvancementPreview(
      tournamentId: TournamentId,
      stageId: TournamentStageId,
      at: Instant = Instant.now()
  ): StageAdvancementSnapshot =
    val tournament = tournamentRepository
      .findById(tournamentId)
      .getOrElse(throw NoSuchElementException(s"Tournament ${tournamentId.value} was not found"))
    val stage = requireStage(tournament, stageId)
    val participants = resolveParticipants(tournament, stage).map(_.id)
    val ranking = tournamentRuleEngine.buildStageRanking(
      tournament,
      stage,
      participants,
      stageRecords(tournamentId, stageId),
      at
    )
    tournamentRuleEngine.projectAdvancement(tournament, stage, ranking, at)

  def stageKnockoutBracket(
      tournamentId: TournamentId,
      stageId: TournamentStageId,
      at: Instant = Instant.now()
  ): KnockoutBracketSnapshot =
    knockoutStageCoordinator.buildProgression(tournamentId, stageId, at)

  def advanceKnockoutStage(
      tournamentId: TournamentId,
      stageId: TournamentStageId,
      actor: AccessPrincipal,
      at: Instant = Instant.now()
  ): Vector[Table] =
    transactionManager.inTransaction {
      val tournament = tournamentRepository
        .findById(tournamentId)
        .getOrElse(throw NoSuchElementException(s"Tournament ${tournamentId.value} was not found"))
      val stage = requireStage(tournament, stageId)

      authorizationService.requirePermission(
        actor,
        Permission.ManageTournamentStages,
        tournamentId = Some(tournamentId)
      )

      val isKnockoutStage =
        stage.format == StageFormat.Knockout ||
          stage.format == StageFormat.Finals ||
          stage.advancementRule.ruleType == AdvancementRuleType.KnockoutElimination

      if !isKnockoutStage then
        throw IllegalArgumentException(
          s"Stage ${stageId.value} is not configured as a knockout stage"
        )

      knockoutStageCoordinator.materializeUnlockedTables(tournamentId, stageId, at)
    }

  def completeStage(
      tournamentId: TournamentId,
      stageId: TournamentStageId,
      actor: AccessPrincipal,
      completedAt: Instant = Instant.now()
  ): Option[StageAdvancementSnapshot] =
    transactionManager.inTransaction {
      tournamentRepository.findById(tournamentId).map { tournament =>
        authorizationService.requirePermission(
          actor,
          Permission.ManageTournamentStages,
          tournamentId = Some(tournamentId)
        )

        val stage = requireStage(tournament, stageId)
        val stageTables = tableRepository.findByTournamentAndStage(tournamentId, stageId)

        if stageTables.size != stage.scheduledTableIds.size then
          throw IllegalArgumentException(
            s"Stage ${stageId.value} cannot complete before every scheduled table is materialized"
          )

        if stageTables.exists(_.status != TableStatus.Archived) then
          throw IllegalArgumentException(
            s"Stage ${stageId.value} cannot complete while tables are still active or under appeal"
          )

        val ranking =
          tournamentRuleEngine.buildStageRanking(
            tournament,
            stage,
            resolveParticipants(tournament, stage).map(_.id),
            stageRecords(tournamentId, stageId),
            completedAt
          )
        val advancement = tournamentRuleEngine.projectAdvancement(tournament, stage, ranking, completedAt)

        tournamentRepository.save(tournament.updateStage(stageId, _.complete))
        advancement
      }
    }

  def settleTournament(
      tournamentId: TournamentId,
      finalStageId: TournamentStageId,
      prizePool: Long,
      payoutRatios: Vector[Double] = Vector(0.5, 0.3, 0.2),
      actor: AccessPrincipal,
      settledAt: Instant = Instant.now()
  ): TournamentSettlementSnapshot =
    transactionManager.inTransaction {
      require(prizePool >= 0L, "Prize pool must be non-negative")

      val tournament = tournamentRepository
        .findById(tournamentId)
        .getOrElse(throw NoSuchElementException(s"Tournament ${tournamentId.value} was not found"))
      val finalStage = requireStage(tournament, finalStageId)

      authorizationService.requirePermission(
        actor,
        Permission.ManageTournamentStages,
        tournamentId = Some(tournamentId)
      )

      val ranking = stageStandings(tournamentId, finalStageId, settledAt)
      val isKnockoutStage =
        finalStage.format == StageFormat.Knockout ||
          finalStage.format == StageFormat.Finals ||
          finalStage.advancementRule.ruleType == AdvancementRuleType.KnockoutElimination

      val resolvedPlayers =
        if isKnockoutStage then
          val bracket = stageKnockoutBracket(tournamentId, finalStageId, settledAt)
          val finalMatch = bracket.rounds.lastOption.flatMap(_.matches.headOption).getOrElse {
            throw IllegalArgumentException(s"Stage ${finalStageId.value} does not contain a final match")
          }
          if !finalMatch.completed then
            throw IllegalArgumentException(
              s"Final knockout match ${finalMatch.id} must be completed before settlement"
            )

          finalMatch.results.sortBy(_.placement).map(_.playerId) ++
            ranking.entries.map(_.playerId).filterNot(playerId =>
              finalMatch.results.exists(_.playerId == playerId)
            )
        else ranking.entries.map(_.playerId)

      val awards = allocatePrizePool(prizePool, payoutRatios, resolvedPlayers.size)
      val rankingByPlayer = ranking.entries.map(entry => entry.playerId -> entry).toMap
      val championId = resolvedPlayers.headOption.getOrElse {
        throw IllegalArgumentException(s"Stage ${finalStageId.value} does not contain any ranked players")
      }

      if tournament.stages.forall(_.status == StageStatus.Completed) && tournament.status != TournamentStatus.Completed then
        tournamentRepository.save(tournament.complete)

      TournamentSettlementSnapshot(
        tournamentId = tournamentId,
        stageId = finalStageId,
        generatedAt = settledAt,
        championId = championId,
        prizePool = prizePool,
        entries = resolvedPlayers.zipWithIndex.map { case (playerId, index) =>
          val standing = rankingByPlayer.getOrElse(
            playerId,
            StageStandingEntry(playerId, 0, 0, 0, 0, 99.0)
          )
          TournamentSettlementEntry(
            playerId = playerId,
            rank = index + 1,
            awardAmount = awards.lift(index).getOrElse(0L),
            finalPoints = standing.totalFinalPoints,
            champion = index == 0
          )
        },
        summary = s"Champion ${championId.value} settled from stage ${finalStageId.value} with prize pool $prizePool."
      )
    }

  private def resolveParticipants(
      tournament: Tournament,
      stage: TournamentStage
  ): Vector[Player] =
    val stagePlayerIds = stage.lineupSubmissions.flatMap(_.activePlayerIds).distinct

    val fallbackPlayerIds =
      val clubMembers = tournament.participatingClubs.flatMap { clubId =>
        clubRepository.findById(clubId).toVector.flatMap(_.members)
      }

      (tournament.participatingPlayers ++ clubMembers).distinct

    val targetPlayerIds =
      if stagePlayerIds.nonEmpty then stagePlayerIds else fallbackPlayerIds

    targetPlayerIds.flatMap { playerId =>
      playerRepository.findById(playerId).filter(_.status == PlayerStatus.Active)
    }

  private def normalizeStage(stage: TournamentStage): TournamentStage =
    if stage.advancementRule.ruleType == AdvancementRuleType.Custom &&
        stage.advancementRule.note.contains("unconfigured")
    then stage.copy(advancementRule = AdvancementRule.defaultFor(stage.format))
    else stage

  private def stageRecords(
      tournamentId: TournamentId,
      stageId: TournamentStageId
  ): Vector[MatchRecord] =
    matchRecordRepository.findAll()
      .filter(record => record.tournamentId == tournamentId && record.stageId == stageId)

  private def requireUniqueStageConfiguration(stages: Vector[TournamentStage]): Unit =
    if stages.map(_.id).distinct.size != stages.size then
      throw IllegalArgumentException("Tournament stages must have unique ids")
    if stages.map(_.order).distinct.size != stages.size then
      throw IllegalArgumentException("Tournament stages must have unique ordering")

  private def requireStage(
      tournament: Tournament,
      stageId: TournamentStageId
  ): TournamentStage =
    tournament.stages
      .find(_.id == stageId)
      .getOrElse(throw NoSuchElementException(s"Stage ${stageId.value} was not found"))

  private def ensureClubActive(club: Club): Unit =
    if club.dissolvedAt.nonEmpty then
      throw IllegalArgumentException(s"Club ${club.id.value} has already been dissolved")

  private def requireActivePlayer(player: Player, context: String): Unit =
    if player.status != PlayerStatus.Active then
      throw IllegalArgumentException(context)

  private def allocatePrizePool(
      prizePool: Long,
      payoutRatios: Vector[Double],
      participantCount: Int
  ): Vector[Long] =
    if prizePool <= 0L || participantCount <= 0 then Vector.fill(participantCount)(0L)
    else
      val normalizedRatios =
        if payoutRatios.isEmpty then Vector(1.0)
        else payoutRatios.map(ratio => math.max(0.0, ratio))

      val ratioSum = normalizedRatios.sum
      val effectiveRatios =
        if ratioSum <= 0.0 then Vector(1.0)
        else normalizedRatios.map(_ / ratioSum)

      val paidSlots = math.min(participantCount, effectiveRatios.size)
      val baseAwards = effectiveRatios.take(paidSlots).map(ratio => math.floor(prizePool.toDouble * ratio).toLong)
      val remainder = prizePool - baseAwards.sum
      val adjustedAwards =
        if baseAwards.isEmpty then Vector.empty
        else baseAwards.updated(0, baseAwards.head + remainder)

      adjustedAwards ++ Vector.fill(participantCount - paidSlots)(0L)

final class TableLifecycleService(
    tableRepository: TableRepository,
    paifuRepository: PaifuRepository,
    matchRecordRepository: MatchRecordRepository,
    knockoutStageCoordinator: KnockoutStageCoordinator,
    eventBus: DomainEventBus,
    transactionManager: TransactionManager = NoOpTransactionManager,
    authorizationService: AuthorizationService = NoOpAuthorizationService
):
  def startTable(
      tableId: TableId,
      startedAt: Instant = Instant.now(),
      actor: AccessPrincipal = AccessPrincipal.system
  ): Option[Table] =
    transactionManager.inTransaction {
      tableRepository.findById(tableId).map { table =>
        authorizationService.requirePermission(
          actor,
          Permission.ManageTournamentStages,
          tournamentId = Some(table.tournamentId)
        )

        tableRepository.save(table.start(startedAt))
      }
    }

  def recordCompletedTable(
      tableId: TableId,
      paifu: Paifu,
      actor: AccessPrincipal = AccessPrincipal.system
  ): Option[Table] =
    transactionManager.inTransaction {
      tableRepository.findById(tableId).map { table =>
        authorizationService.requirePermission(
          actor,
          Permission.ManageTournamentStages,
          tournamentId = Some(table.tournamentId)
        )
        validatePaifu(table, paifu)

        if matchRecordRepository.findByTable(tableId).nonEmpty then
          throw IllegalArgumentException(s"Table ${tableId.value} has already been archived")

        val provisionalRecord =
          MatchRecord.fromTableAndPaifu(table, paifu, paifu.metadata.recordedAt, actor.playerId)
        val linkedPaifu = paifu.copy(
          metadata = paifu.metadata.copy(matchRecordId = Some(provisionalRecord.id))
        )
        val storedPaifu = paifuRepository.save(linkedPaifu)
        val storedRecord =
          matchRecordRepository.save(provisionalRecord.copy(paifuId = Some(storedPaifu.id)))

        val archivedTable = tableRepository.save(
          table
            .enterScoring(paifu.metadata.recordedAt)
            .archive(storedRecord.id, storedPaifu.id, paifu.metadata.recordedAt)
        )

        eventBus.publish(
          MatchRecordArchived(
            tableId = table.id,
            tournamentId = table.tournamentId,
            stageId = table.stageId,
            matchRecord = storedRecord,
            paifu = Some(storedPaifu),
            occurredAt = paifu.metadata.recordedAt
          )
        )

        if table.bracketMatchId.nonEmpty then
          knockoutStageCoordinator.materializeUnlockedTables(
            table.tournamentId,
            table.stageId,
            paifu.metadata.recordedAt
          )

        archivedTable
      }
    }

  def forceReset(
      tableId: TableId,
      note: String,
      actor: AccessPrincipal,
      at: Instant = Instant.now()
  ): Option[Table] =
    transactionManager.inTransaction {
      tableRepository.findById(tableId).map { table =>
        authorizationService.requirePermission(
          actor,
          Permission.ResetTableState,
          tournamentId = Some(table.tournamentId)
        )

        tableRepository.save(table.forceReset(note, at))
      }
    }

  private def validatePaifu(table: Table, paifu: Paifu): Unit =
    val scheduledSeatsByPlayer = table.seats.map(seat => seat.playerId -> seat).toMap
    val seatPlayerIds = scheduledSeatsByPlayer.keySet

    require(paifu.metadata.tableId == table.id, "Paifu table id does not match the table")
    require(
      paifu.metadata.tournamentId == table.tournamentId,
      "Paifu tournament id does not match the table"
    )
    require(paifu.metadata.stageId == table.stageId, "Paifu stage id does not match the table")
    require(
      paifu.metadata.seats.toSet == table.seats.toSet,
      "Paifu seat map does not match the scheduled table"
    )
    require(paifu.rounds.nonEmpty, "Paifu must contain at least one round")
    require(paifu.finalStandings.size == 4, "Paifu must provide four final standings")
    require(
      paifu.finalStandings.map(_.placement).distinct.size == 4,
      "Paifu placements must be unique"
    )
    require(
      paifu.finalStandings.forall(standing =>
        scheduledSeatsByPlayer.get(standing.playerId).exists(_.seat == standing.seat)
      ),
      "Paifu final standing seats must match the scheduled table"
    )
    require(
      paifu.finalStandings.map(_.finalPoints).sum == table.seats.map(_.initialPoints).sum,
      "Paifu final points must preserve the table point total"
    )

    paifu.rounds.zipWithIndex.foreach { (round, index) =>
      require(
        round.initialHands.keySet == seatPlayerIds,
        s"Round ${index + 1} must provide initial hands for all seated players"
      )

      val terminalActions = round.actions.filter(action =>
        action.actionType == PaifuActionType.Win || action.actionType == PaifuActionType.DrawGame
      )
      require(
        terminalActions.nonEmpty,
        s"Round ${index + 1} must end with a terminal action"
      )
      require(
        terminalActions.size == 1,
        s"Round ${index + 1} must contain exactly one terminal action"
      )

      round.result.outcome match
        case HandOutcome.Ron | HandOutcome.Tsumo =>
          require(
            terminalActions.head.actionType == PaifuActionType.Win,
            s"Round ${index + 1} winning result must end with a Win action"
          )
        case HandOutcome.ExhaustiveDraw | HandOutcome.AbortiveDraw =>
          require(
            terminalActions.head.actionType == PaifuActionType.DrawGame,
            s"Round ${index + 1} drawn result must end with a DrawGame action"
          )
    }

    val expectedFinalPoints = paifu.expectedFinalPoints
    require(
      paifu.finalStandings.forall(standing =>
        expectedFinalPoints.get(standing.playerId).contains(standing.finalPoints)
      ),
      "Paifu final standings must match the cumulative round score changes"
    )

final class AppealApplicationService(
    appealTicketRepository: AppealTicketRepository,
    tableRepository: TableRepository,
    knockoutStageCoordinator: KnockoutStageCoordinator,
    eventBus: DomainEventBus,
    transactionManager: TransactionManager = NoOpTransactionManager,
    authorizationService: AuthorizationService = NoOpAuthorizationService
):
  def fileAppeal(
      tableId: TableId,
      openedBy: PlayerId,
      description: String,
      attachments: Vector[AppealAttachment] = Vector.empty,
      actor: AccessPrincipal,
      createdAt: Instant = Instant.now()
  ): Option[AppealTicket] =
    transactionManager.inTransaction {
      tableRepository.findById(tableId).map { table =>
        require(description.trim.nonEmpty, "Appeal description cannot be empty")
        authorizationService.requirePermission(
          actor,
          Permission.FileAppealTicket,
          subjectPlayerId = Some(openedBy)
        )

        if !table.seats.exists(_.playerId == openedBy) then
          throw IllegalArgumentException(s"Player ${openedBy.value} is not seated at table ${tableId.value}")
        if table.status == TableStatus.Archived then
          throw IllegalArgumentException(s"Archived table ${tableId.value} cannot accept new appeals")
        if appealTicketRepository.findAll().exists(ticket =>
            ticket.tableId == tableId &&
              (ticket.status == AppealStatus.Open ||
                ticket.status == AppealStatus.UnderReview ||
                ticket.status == AppealStatus.Escalated)
          )
        then
          throw IllegalArgumentException(
            s"Table ${tableId.value} already has an active appeal ticket"
          )

        val ticket = AppealTicket(
          id = IdGenerator.appealTicketId(),
          tableId = table.id,
          tournamentId = table.tournamentId,
          stageId = table.stageId,
          openedBy = openedBy,
          description = description,
          attachments = attachments,
          createdAt = createdAt,
          updatedAt = createdAt
        )

        val savedTicket = appealTicketRepository.save(ticket)
        tableRepository.save(table.flagAppeal(savedTicket.id, Some(description)))
        eventBus.publish(AppealTicketFiled(savedTicket, createdAt))
        savedTicket
      }
    }

  def resolveAppeal(
      ticketId: AppealTicketId,
      verdict: String,
      actor: AccessPrincipal,
      resolvedAt: Instant = Instant.now(),
      note: Option[String] = None
  ): Option[AppealTicket] =
    adjudicateAppeal(
      ticketId = ticketId,
      decision = AppealDecisionType.Resolve,
      verdict = verdict,
      actor = actor,
      adjudicatedAt = resolvedAt,
      tableResolution = Some(AppealTableResolution.RestorePriorState),
      note = note
    )

  def adjudicateAppeal(
      ticketId: AppealTicketId,
      decision: AppealDecisionType,
      verdict: String,
      actor: AccessPrincipal,
      adjudicatedAt: Instant = Instant.now(),
      tableResolution: Option[AppealTableResolution] = None,
      note: Option[String] = None
  ): Option[AppealTicket] =
    transactionManager.inTransaction {
      appealTicketRepository.findById(ticketId).map { ticket =>
        authorizationService.requirePermission(
          actor,
          Permission.ResolveAppeal,
          tournamentId = Some(ticket.tournamentId)
        )

        val operatorId = actor.playerId.getOrElse(ticket.openedBy)
        val reviewedTicket =
          if ticket.status == AppealStatus.UnderReview then ticket
          else ticket.markUnderReview(operatorId, adjudicatedAt, note)

        val adjudicatedTicket =
          decision match
            case AppealDecisionType.Resolve =>
              reviewedTicket.resolve(operatorId, verdict, adjudicatedAt, note)
            case AppealDecisionType.Reject =>
              reviewedTicket.reject(operatorId, verdict, adjudicatedAt, note)
            case AppealDecisionType.Escalate =>
              reviewedTicket.escalate(operatorId, verdict, adjudicatedAt, note)

        appealTicketRepository.save(adjudicatedTicket)

        if decision != AppealDecisionType.Escalate then
          tableRepository.findById(ticket.tableId).foreach { table =>
            val updatedTable =
              tableResolution.getOrElse(AppealTableResolution.RestorePriorState) match
                case AppealTableResolution.ForceReset =>
                  table.forceReset(
                    note.getOrElse(s"Appeal ${ticketId.value} adjudication requested reset"),
                    adjudicatedAt
                  )
                case resolution =>
                  table.resolveAppeal(resolution, note)

            tableRepository.save(updatedTable)

            if updatedTable.bracketMatchId.nonEmpty && updatedTable.status != TableStatus.Archived then
              knockoutStageCoordinator.reconcileAfterMatchMutation(
                updatedTable.tournamentId,
                updatedTable.stageId,
                updatedTable.bracketMatchId.get,
                adjudicatedAt
              )
          }

        decision match
          case AppealDecisionType.Resolve =>
            eventBus.publish(AppealTicketResolved(adjudicatedTicket, adjudicatedAt))
          case _ =>
            ()

        eventBus.publish(
          AppealTicketAdjudicated(
            ticket = adjudicatedTicket,
            decision = decision,
            tableResolution =
              if decision == AppealDecisionType.Escalate then None
              else tableResolution.orElse(Some(AppealTableResolution.RestorePriorState)),
            occurredAt = adjudicatedAt
          )
        )
        adjudicatedTicket
      }
    }

final class SuperAdminService(
    playerRepository: PlayerRepository,
    clubRepository: ClubRepository,
    globalDictionaryRepository: GlobalDictionaryRepository,
    eventBus: DomainEventBus,
    transactionManager: TransactionManager = NoOpTransactionManager,
    authorizationService: AuthorizationService = NoOpAuthorizationService
):
  def grantSuperAdmin(
      playerId: PlayerId,
      actor: AccessPrincipal = AccessPrincipal.system,
      grantedAt: Instant = Instant.now()
  ): Option[Player] =
    transactionManager.inTransaction {
      if !actor.isSuperAdmin then
        throw AuthorizationFailure("Only an existing super admin can grant super admin access")

      playerRepository.findById(playerId).map { player =>
        playerRepository.save(player.grantRole(RoleGrant.superAdmin(grantedAt, actor.playerId)))
      }
    }

  def upsertDictionary(
      key: String,
      value: String,
      actor: AccessPrincipal,
      note: Option[String] = None,
      updatedAt: Instant = Instant.now()
  ): GlobalDictionaryEntry =
    transactionManager.inTransaction {
      authorizationService.requirePermission(actor, Permission.ManageGlobalDictionary)

      val entry = GlobalDictionaryEntry(
        key = key,
        value = value,
        updatedAt = updatedAt,
        updatedBy = actor.playerId.getOrElse(PlayerId("system")),
        note = note
      )

      val saved = globalDictionaryRepository.save(entry)
      eventBus.publish(GlobalDictionaryUpdated(saved, updatedAt))
      saved
    }

  def banPlayer(
      playerId: PlayerId,
      reason: String,
      actor: AccessPrincipal,
      at: Instant = Instant.now()
  ): Option[Player] =
    transactionManager.inTransaction {
      authorizationService.requirePermission(actor, Permission.BanRegisteredPlayer)
      require(reason.trim.nonEmpty, "Ban reason cannot be empty")

      playerRepository.findById(playerId).map { player =>
        val banned = playerRepository.save(player.ban(reason))
        eventBus.publish(PlayerBanned(playerId, reason, at))
        banned
      }
    }

  def dissolveClub(
      clubId: ClubId,
      actor: AccessPrincipal,
      at: Instant = Instant.now()
  ): Option[Club] =
    transactionManager.inTransaction {
      authorizationService.requirePermission(actor, Permission.DissolveClub)

      clubRepository.findById(clubId).map { club =>
        if club.dissolvedAt.nonEmpty then
          throw IllegalArgumentException(s"Club ${clubId.value} has already been dissolved")

        club.members.foreach { memberId =>
          playerRepository.findById(memberId).foreach { player =>
            playerRepository.save(
              player
                .leaveClub(clubId)
                .revokeClubAdmin(clubId)
            )
          }
        }

        clubRepository.findActive()
          .filterNot(_.id == clubId)
          .filter(_.relations.exists(_.targetClubId == clubId))
          .foreach { relatedClub =>
            clubRepository.save(relatedClub.removeRelation(clubId))
          }

        val dissolved = clubRepository.save(
          club.dissolve(actor.playerId.getOrElse(club.creator), at)
        )
        eventBus.publish(ClubDissolved(clubId, at))
        dissolved
      }
    }

final class RatingProjectionSubscriber(
    playerRepository: PlayerRepository,
    ratingService: RatingService
) extends DomainEventSubscriber:
  override def handle(event: DomainEvent): Unit =
    event match
      case MatchRecordArchived(_, _, _, matchRecord, _, _) =>
        val players = matchRecord.seatResults.flatMap { result =>
          playerRepository.findById(result.playerId)
        }

        val standings = matchRecord.seatResults.map { result =>
          FinalStanding(
            playerId = result.playerId,
            seat = result.seat,
            finalPoints = result.finalPoints,
            placement = result.placement,
            uma = result.uma,
            oka = result.oka
          )
        }

        val deltas = ratingService.calculateDeltas(players, standings)

        deltas.foreach { delta =>
          playerRepository.findById(delta.playerId).foreach { player =>
            playerRepository.save(player.applyElo(delta.delta))
          }
        }

      case _ =>
        ()

final class ClubProjectionSubscriber(
    clubRepository: ClubRepository,
    playerRepository: PlayerRepository
) extends DomainEventSubscriber:
  override def handle(event: DomainEvent): Unit =
    event match
      case MatchRecordArchived(_, _, _, matchRecord, _, _) =>
        val impactedClubIds = matchRecord.seatResults.flatMap { result =>
          playerRepository.findById(result.playerId).flatMap(_.clubId)
        }.distinct

        matchRecord.seatResults.foreach { result =>
          playerRepository.findById(result.playerId).flatMap(_.clubId).foreach { clubId =>
            clubRepository.findById(clubId).foreach { club =>
              clubRepository.save(club.addPoints(result.scoreDelta))
            }
          }
        }

        impactedClubIds.foreach { clubId =>
          clubRepository.findById(clubId).foreach { club =>
            val memberElos = club.members.flatMap(memberId => playerRepository.findById(memberId).map(_.elo))
            val averageElo =
              if memberElos.isEmpty then 0.0 else memberElos.sum.toDouble / memberElos.size.toDouble
            val powerRating = averageElo + club.totalPoints.toDouble / 1000.0
            clubRepository.save(club.updatePowerRating(round2(powerRating)))
          }
        }

      case _ =>
        ()

  private def round2(value: Double): Double =
    BigDecimal(value).setScale(2, BigDecimal.RoundingMode.HALF_UP).toDouble

final class DashboardProjectionSubscriber(
    matchRecordRepository: MatchRecordRepository,
    paifuRepository: PaifuRepository,
    playerRepository: PlayerRepository,
    clubRepository: ClubRepository,
    dashboardRepository: DashboardRepository
) extends DomainEventSubscriber:
  override def handle(event: DomainEvent): Unit =
    event match
      case MatchRecordArchived(_, _, _, matchRecord, _, occurredAt) =>
        val impactedPlayers = matchRecord.playerIds.distinct

        impactedPlayers.foreach { playerId =>
          dashboardRepository.save(buildPlayerDashboard(playerId, occurredAt))
        }

        impactedPlayers
          .flatMap(playerId => playerRepository.findById(playerId).flatMap(_.clubId))
          .distinct
          .foreach { clubId =>
            clubRepository.findById(clubId).foreach { club =>
              dashboardRepository.save(buildClubDashboard(club, occurredAt))
            }
          }

      case _ =>
        ()

  private def buildPlayerDashboard(playerId: PlayerId, at: Instant): Dashboard =
    val records = matchRecordRepository.findByPlayer(playerId)
    val paifus = paifuRepository.findByPlayer(playerId)
    val rounds = paifus.flatMap(_.rounds)
    val placements = records.flatMap(_.seatResults.find(_.playerId == playerId)).map(_.placement.toDouble)
    val topFinishes = records.flatMap(_.seatResults.find(_.playerId == playerId)).count(_.placement == 1)
    val winPointSamples = rounds.flatMap { round =>
      if round.result.winner.contains(playerId) then
        round.result.scoreChanges.find(_.playerId == playerId).map(_.delta.toDouble)
      else None
    }
    val riichiCount = rounds.flatMap(_.actions).count { action =>
      action.actor.contains(playerId) && action.actionType == PaifuActionType.Riichi
    }
    val dealInCount = rounds.count { round =>
      round.result.outcome == HandOutcome.Ron && round.result.target.contains(playerId)
    }
    val winCount = rounds.count(_.result.winner.contains(playerId))
    val shantenSamples = rounds
      .flatMap(_.actions)
      .flatMap { action =>
        if action.actor.contains(playerId) then action.shantenAfterAction.map(_.toDouble) else None
      }

    Dashboard(
      owner = DashboardOwner.Player(playerId),
      sampleSize = rounds.size,
      dealInRate = ratio(dealInCount, rounds.size),
      winRate = ratio(winCount, rounds.size),
      averageWinPoints = average(winPointSamples),
      riichiRate = ratio(riichiCount, rounds.size),
      averagePlacement = average(placements),
      topFinishRate = ratio(topFinishes, records.size),
      defenseStability = round2(1.0 - ratio(dealInCount, rounds.size)),
      ukeireExpectation = average(shantenSamples.map(sample => 14.0 - sample)),
      shantenTrajectory = shantenSamples.map(round2),
      lastUpdatedAt = at
    )

  private def buildClubDashboard(club: Club, at: Instant): Dashboard =
    val memberDashboards = club.members.flatMap { playerId =>
      dashboardRepository.findByOwner(DashboardOwner.Player(playerId))
    }

    if memberDashboards.isEmpty then Dashboard.empty(DashboardOwner.Club(club.id), at)
    else
      Dashboard(
        owner = DashboardOwner.Club(club.id),
        sampleSize = memberDashboards.map(_.sampleSize).sum,
        dealInRate = weightedAverage(memberDashboards, _.dealInRate),
        winRate = weightedAverage(memberDashboards, _.winRate),
        averageWinPoints = weightedAverage(memberDashboards, _.averageWinPoints),
        riichiRate = weightedAverage(memberDashboards, _.riichiRate),
        averagePlacement = weightedAverage(memberDashboards, _.averagePlacement),
        topFinishRate = weightedAverage(memberDashboards, _.topFinishRate),
        defenseStability = weightedAverage(memberDashboards, _.defenseStability),
        ukeireExpectation = weightedAverage(memberDashboards, _.ukeireExpectation),
        shantenTrajectory = averageTrajectory(memberDashboards.map(_.shantenTrajectory)),
        lastUpdatedAt = at
      )

  private def ratio(numerator: Int, denominator: Int): Double =
    if denominator <= 0 then 0.0
    else round2(numerator.toDouble / denominator.toDouble)

  private def average(values: Vector[Double]): Double =
    if values.isEmpty then 0.0
    else round2(values.sum / values.size.toDouble)

  private def weightedAverage(
      dashboards: Vector[Dashboard],
      selector: Dashboard => Double
  ): Double =
    val totalWeight = dashboards.map(_.sampleSize).sum
    if totalWeight <= 0 then 0.0
    else
      round2(
        dashboards.map(dashboard => selector(dashboard) * dashboard.sampleSize).sum /
          totalWeight.toDouble
      )

  private def averageTrajectory(trajectories: Vector[Vector[Double]]): Vector[Double] =
    val maxLength = trajectories.map(_.size).foldLeft(0)(math.max)

    (0 until maxLength).toVector.flatMap { index =>
      val samples = trajectories.flatMap(_.lift(index))
      if samples.isEmpty then None
      else Some(round2(samples.sum / samples.size.toDouble))
    }

  private def round2(value: Double): Double =
    BigDecimal(value).setScale(2, BigDecimal.RoundingMode.HALF_UP).toDouble
