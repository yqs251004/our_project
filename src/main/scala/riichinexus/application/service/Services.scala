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
      val creator = playerRepository
        .findById(creatorId)
        .getOrElse(throw NoSuchElementException(s"Player ${creatorId.value} was not found"))

      if !actor.isSuperAdmin && actor.playerId.exists(_ != creatorId) then
        throw AuthorizationFailure("Only the creator or a super admin can create the club")

      val club = clubRepository.findByName(name) match
        case Some(existing) =>
          existing
            .addMember(creatorId)
            .grantAdmin(creatorId)
        case None =>
          Club(
            id = IdGenerator.clubId(),
            name = name,
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
        authorizationService.requirePermission(
          actor,
          Permission.ManageClubMembership,
          clubId = Some(clubId)
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
      clubRepository.findById(clubId).map { club =>
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
    }

  def updateRelation(
      clubId: ClubId,
      relation: ClubRelation,
      actor: AccessPrincipal
  ): Option[Club] =
    transactionManager.inTransaction {
      clubRepository.findById(clubId).map { club =>
        authorizationService.requirePermission(
          actor,
          Permission.SetClubTitle,
          clubId = Some(clubId)
        )

        clubRepository.save(club.upsertRelation(relation))
      }
    }

final class TournamentApplicationService(
    tournamentRepository: TournamentRepository,
    playerRepository: PlayerRepository,
    clubRepository: ClubRepository,
    tableRepository: TableRepository,
    seatingPolicy: SeatingPolicy,
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
      val normalizedStages = stages.map(normalizeStage).sortBy(_.order)

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
      tournamentRepository.findById(tournamentId).map { tournament =>
        tournamentRepository.save(tournament.registerPlayer(playerId))
      }
    }

  def registerClub(tournamentId: TournamentId, clubId: ClubId): Option[Tournament] =
    transactionManager.inTransaction {
      tournamentRepository.findById(tournamentId).map { tournament =>
        tournamentRepository.save(tournament.registerClub(clubId))
      }
    }

  def whitelistPlayer(tournamentId: TournamentId, playerId: PlayerId): Option[Tournament] =
    transactionManager.inTransaction {
      tournamentRepository.findById(tournamentId).map { tournament =>
        tournamentRepository.save(tournament.whitelistPlayer(playerId))
      }
    }

  def whitelistClub(tournamentId: TournamentId, clubId: ClubId): Option[Tournament] =
    transactionManager.inTransaction {
      tournamentRepository.findById(tournamentId).map { tournament =>
        tournamentRepository.save(tournament.whitelistClub(clubId))
      }
    }

  def publishTournament(tournamentId: TournamentId): Option[Tournament] =
    transactionManager.inTransaction {
      tournamentRepository.findById(tournamentId).map { tournament =>
        tournamentRepository.save(tournament.publish)
      }
    }

  def startTournament(tournamentId: TournamentId): Option[Tournament] =
    transactionManager.inTransaction {
      tournamentRepository.findById(tournamentId).map { tournament =>
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
        authorizationService.requirePermission(
          actor,
          Permission.SubmitTournamentLineup,
          clubId = Some(submission.clubId)
        )

        val isClubRegistered =
          tournament.participatingClubs.contains(submission.clubId) ||
            tournament.whitelist.exists(_.clubId.contains(submission.clubId))

        if !isClubRegistered then
          throw IllegalArgumentException(
            s"Club ${submission.clubId.value} is not whitelisted for tournament ${tournamentId.value}"
          )

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

        val tournamentPlayers = resolveParticipants(tournament, stage)
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

final class TableLifecycleService(
    tableRepository: TableRepository,
    paifuRepository: PaifuRepository,
    matchRecordRepository: MatchRecordRepository,
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
        validatePaifu(table, paifu)

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

final class AppealApplicationService(
    appealTicketRepository: AppealTicketRepository,
    tableRepository: TableRepository,
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
        authorizationService.requirePermission(
          actor,
          Permission.FileAppealTicket,
          subjectPlayerId = Some(openedBy)
        )

        if !table.seats.exists(_.playerId == openedBy) then
          throw IllegalArgumentException(s"Player ${openedBy.value} is not seated at table ${tableId.value}")

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
    transactionManager.inTransaction {
      appealTicketRepository.findById(ticketId).map { ticket =>
        authorizationService.requirePermission(
          actor,
          Permission.ResolveAppeal,
          tournamentId = Some(ticket.tournamentId)
        )

        val operatorId = actor.playerId.getOrElse(ticket.openedBy)
        val resolvedTicket = ticket.resolve(operatorId, verdict, resolvedAt, note)

        appealTicketRepository.save(resolvedTicket)
        tableRepository.findById(ticket.tableId).foreach { table =>
          tableRepository.save(table.resolveAppeal(note))
        }
        eventBus.publish(AppealTicketResolved(resolvedTicket, resolvedAt))
        resolvedTicket
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
        club.members.foreach { memberId =>
          playerRepository.findById(memberId).foreach { player =>
            playerRepository.save(
              player
                .leaveClub(clubId)
                .revokeClubAdmin(clubId)
            )
          }
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
