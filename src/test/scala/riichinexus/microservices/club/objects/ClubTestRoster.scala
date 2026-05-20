package riichinexus.microservices.club.objects

import java.time.Instant
import java.util.NoSuchElementException

import riichinexus.application.ports.*
import riichinexus.domain.model.*
import riichinexus.domain.service.*

final class ClubTestRoster(
    clubRepository: ClubRepository,
    playerRepository: PlayerRepository,
    globalDictionaryRepository: GlobalDictionaryRepository,
    dashboardRepository: DashboardRepository,
    auditEventRepository: AuditEventRepository,
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

      val savedCreator = playerRepository.save(updatedCreator)
      ensurePlayerDashboard(savedCreator.id, dashboardRepository, createdAt)
      clubRepository.save(
        refreshClubProjection(
          club,
          playerRepository,
          globalDictionaryRepository,
          dashboardRepository,
          createdAt
        )
      )
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
        requireClubCapability(
          authorizationService = authorizationService,
          actor = actor,
          club = club,
          permission = Permission.ManageClubMembership,
          delegatedPrivileges = Set(ClubPrivilege.ApproveRoster)
        )

        val savedPlayer = playerRepository.save(player.joinClub(clubId))
        ensurePlayerDashboard(savedPlayer.id, dashboardRepository, Instant.now())
        clubRepository.save(
          refreshClubProjection(
            club.addMember(playerId),
            playerRepository,
            globalDictionaryRepository,
            dashboardRepository,
            Instant.now()
          )
        )
    }

  def removeMember(
      clubId: ClubId,
      playerId: PlayerId,
      actor: AccessPrincipal
  ): Option[Club] =
    transactionManager.inTransaction {
      for
        club <- clubRepository.findById(clubId)
        player <- playerRepository.findById(playerId)
      yield
        ensureClubActive(club)
        requireClubMember(club, playerId, "remove member")
        requireClubCapability(
          authorizationService = authorizationService,
          actor = actor,
          club = club,
          permission = Permission.ManageClubMembership,
          delegatedPrivileges = Set(ClubPrivilege.ApproveRoster)
        )

        if club.creator == playerId then
          throw IllegalArgumentException(
            s"Club creator ${playerId.value} cannot be removed from active club ${clubId.value}"
          )

        if club.admins.contains(playerId) && club.admins.size <= 1 then
          throw IllegalArgumentException(
            s"Club ${clubId.value} must retain at least one club admin before removing ${playerId.value}"
          )

        playerRepository.save(
          player
            .leaveClub(clubId)
            .revokeClubAdmin(clubId)
        )
        clubRepository.save(
          refreshClubProjection(
            club.removeMember(playerId),
            playerRepository,
            globalDictionaryRepository,
            dashboardRepository,
            Instant.now()
          )
        )
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

  def revokeAdmin(
      clubId: ClubId,
      playerId: PlayerId,
      actor: AccessPrincipal
  ): Option[Club] =
    transactionManager.inTransaction {
      for
        club <- clubRepository.findById(clubId)
        player <- playerRepository.findById(playerId)
      yield
        ensureClubActive(club)
        requireClubMember(club, playerId, "revoke club admin")
        authorizationService.requirePermission(
          actor,
          Permission.AssignClubAdmin,
          clubId = Some(clubId)
        )

        if !club.admins.contains(playerId) then
          throw IllegalArgumentException(
            s"Player ${playerId.value} is not a club admin of club ${clubId.value}"
          )

        if club.admins.size <= 1 then
          throw IllegalArgumentException(
            s"Club ${clubId.value} must retain at least one club admin"
          )

        playerRepository.save(player.revokeClubAdmin(clubId))
        clubRepository.save(club.revokeAdmin(playerId))
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
        val updatedClub = clubRepository.save(
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
        auditEventRepository.save(
          AuditEventEntry(
            id = IdGenerator.auditEventId(),
            aggregateType = "club",
            aggregateId = clubId.value,
            eventType = "ClubTitleAssigned",
            occurredAt = assignedAt,
            actorId = actor.playerId,
            details = Map(
              "playerId" -> playerId.value,
              "title" -> title
            ),
            note = note
          )
        )
        updatedClub
    }

  def clearInternalTitle(
      clubId: ClubId,
      playerId: PlayerId,
      actor: AccessPrincipal,
      clearedAt: Instant = Instant.now(),
      note: Option[String] = None
  ): Option[Club] =
    transactionManager.inTransaction {
      for
        club <- clubRepository.findById(clubId)
        player <- playerRepository.findById(playerId)
      yield
        ensureClubActive(club)
        requireActivePlayer(player, s"Player ${playerId.value} cannot clear club title")
        requireClubMember(club, playerId, "clear internal title")
        authorizationService.requirePermission(
          actor,
          Permission.SetClubTitle,
          clubId = Some(clubId)
        )

        val existingAssignment = club.titleAssignments.find(_.playerId == playerId)
          .getOrElse(
            throw NoSuchElementException(
              s"Player ${playerId.value} does not hold a title in club ${clubId.value}"
            )
          )

        val updatedClub = clubRepository.save(club.clearInternalTitle(playerId))
        auditEventRepository.save(
          AuditEventEntry(
            id = IdGenerator.auditEventId(),
            aggregateType = "club",
            aggregateId = clubId.value,
            eventType = "ClubTitleCleared",
            occurredAt = clearedAt,
            actorId = actor.playerId,
            details = Map(
              "playerId" -> playerId.value,
              "title" -> existingAssignment.title
            ),
            note = note
          )
        )
        updatedClub
    }

  private val ClubPowerEloWeightKey = GlobalDictionaryRegistry.ClubPowerEloWeightKey
  private val ClubPowerPointWeightKey = GlobalDictionaryRegistry.ClubPowerPointWeightKey
  private val ClubPowerBaseBonusKey = GlobalDictionaryRegistry.ClubPowerBaseBonusKey

  private final case class ClubPowerConfig(
      eloWeight: Double = 1.0,
      pointWeight: Double = 0.001,
      baseBonus: Double = 0.0
  )

  private final case class DictionarySnapshot(
      valuesByNormalizedKey: Map[String, String]
  )

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

  private def requireClubCapability(
      authorizationService: AuthorizationService,
      actor: AccessPrincipal,
      club: Club,
      permission: Permission,
      delegatedPrivileges: Set[String]
  ): Unit =
    val hasBasePermission = authorizationService.can(actor, permission, clubId = Some(club.id))
    val hasDelegatedPrivilege = actor.playerId.exists { playerId =>
      club.members.contains(playerId) &&
      delegatedPrivileges.exists(privilege => club.hasPrivilege(playerId, privilege))
    }

    if !hasBasePermission && !hasDelegatedPrivilege then
      throw AuthorizationFailure(
        s"${actor.displayName} is not allowed to perform $permission in club ${club.id.value}"
      )

  private def ensurePlayerDashboard(
      playerId: PlayerId,
      dashboardRepository: DashboardRepository,
      at: Instant
  ): Unit =
    val owner = DashboardOwner.Player(playerId)
    if dashboardRepository.findByOwner(owner).isEmpty then
      dashboardRepository.save(Dashboard.empty(owner, at))

  private def refreshClubProjection(
      club: Club,
      playerRepository: PlayerRepository,
      globalDictionaryRepository: GlobalDictionaryRepository,
      dashboardRepository: DashboardRepository,
      at: Instant
  ): Club =
    val refreshedClub = club.updatePowerRating(
      calculateClubPowerRating(club, playerRepository, dictionarySnapshot(globalDictionaryRepository))
    )
    dashboardRepository.save(buildClubDashboard(refreshedClub, playerRepository, dashboardRepository, at))
    refreshedClub

  private def buildClubDashboard(
      club: Club,
      playerRepository: PlayerRepository,
      dashboardRepository: DashboardRepository,
      at: Instant
  ): Dashboard =
    val existingVersion = dashboardRepository.findByOwner(DashboardOwner.Club(club.id)).map(_.version).getOrElse(0)
    val memberDashboards = activeMemberDashboards(club, playerRepository, dashboardRepository)

    if memberDashboards.isEmpty then Dashboard.empty(DashboardOwner.Club(club.id), at).copy(version = existingVersion)
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
        lastUpdatedAt = at,
        version = existingVersion
      )

  private def activeMemberDashboards(
      club: Club,
      playerRepository: PlayerRepository,
      dashboardRepository: DashboardRepository
  ): Vector[Dashboard] =
    club.members.flatMap { playerId =>
      playerRepository.findById(playerId)
        .filter(_.status == PlayerStatus.Active)
        .flatMap(_ => dashboardRepository.findByOwner(DashboardOwner.Player(playerId)))
    }

  private def calculateClubPowerRating(
      club: Club,
      playerRepository: PlayerRepository,
      dictionarySnapshot: DictionarySnapshot
  ): Double =
    val memberElos = club.members.flatMap(memberId =>
      playerRepository.findById(memberId).filter(_.status == PlayerStatus.Active).map(_.elo)
    )
    val averageElo =
      if memberElos.isEmpty then 0.0 else memberElos.sum.toDouble / memberElos.size.toDouble
    val config = currentClubPowerConfig(dictionarySnapshot)
    round2(averageElo * config.eloWeight + club.totalPoints.toDouble * config.pointWeight + config.baseBonus)

  private def currentClubPowerConfig(
      dictionarySnapshot: DictionarySnapshot
  ): ClubPowerConfig =
    ClubPowerConfig(
      eloWeight = readDouble(dictionarySnapshot, ClubPowerEloWeightKey).filter(_ >= 0.0).getOrElse(1.0),
      pointWeight = readDouble(dictionarySnapshot, ClubPowerPointWeightKey).filter(_ >= 0.0).getOrElse(0.001),
      baseBonus = readDouble(dictionarySnapshot, ClubPowerBaseBonusKey).getOrElse(0.0)
    )

  private def dictionarySnapshot(
      globalDictionaryRepository: GlobalDictionaryRepository
  ): DictionarySnapshot =
    DictionarySnapshot(
      globalDictionaryRepository.findAll()
        .iterator
        .map(entry => GlobalDictionaryRegistry.normalizeKey(entry.key) -> entry.value.trim)
        .filter(_._2.nonEmpty)
        .toMap
    )

  private def readDouble(
      dictionarySnapshot: DictionarySnapshot,
      key: String
  ): Option[Double] =
    dictionarySnapshot.valuesByNormalizedKey
      .get(GlobalDictionaryRegistry.normalizeKey(key))
      .flatMap(value => scala.util.Try(value.trim.toDouble).toOption.filter(_.isFinite))

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

  private def round2(value: Double): Double =
    BigDecimal(value).setScale(2, BigDecimal.RoundingMode.HALF_UP).toDouble
