package riichinexus.microservices.club.api

import java.time.Instant
import java.util.NoSuchElementException

import riichinexus.application.ports.*
import riichinexus.domain.model.*
import riichinexus.domain.service.*

final class ClubRosterService(
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
      ClubPolicySupport.requireActivePlayer(creator, s"Player ${creatorId.value} cannot create a club")

      if !actor.isSuperAdmin && actor.playerId.exists(_ != creatorId) then
        throw AuthorizationFailure("Only the creator or a super admin can create the club")

      val club = clubRepository.findByName(normalizedName) match
        case Some(existing) =>
          ClubPolicySupport.ensureClubActive(existing)
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
      ClubProjectionSupport.ensurePlayerDashboard(savedCreator.id, dashboardRepository, createdAt)
      clubRepository.save(
        ClubProjectionSupport.refreshClubProjection(
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
        ClubPolicySupport.ensureClubActive(club)
        ClubPolicySupport.requireActivePlayer(player, s"Player ${playerId.value} cannot join club ${clubId.value}")
        ClubPolicySupport.requireClubCapability(
          authorizationService = authorizationService,
          actor = actor,
          club = club,
          permission = Permission.ManageClubMembership,
          delegatedPrivileges = Set(ClubPrivilege.ApproveRoster)
        )

        val savedPlayer = playerRepository.save(player.joinClub(clubId))
        ClubProjectionSupport.ensurePlayerDashboard(savedPlayer.id, dashboardRepository, Instant.now())
        clubRepository.save(
          ClubProjectionSupport.refreshClubProjection(
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
        ClubPolicySupport.ensureClubActive(club)
        ClubPolicySupport.requireClubMember(club, playerId, "remove member")
        ClubPolicySupport.requireClubCapability(
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
          ClubProjectionSupport.refreshClubProjection(
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
        ClubPolicySupport.ensureClubActive(club)
        ClubPolicySupport.requireActivePlayer(player, s"Player ${playerId.value} cannot be granted club admin")
        ClubPolicySupport.requireClubMember(club, playerId, "assign club admin")
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
        ClubPolicySupport.ensureClubActive(club)
        ClubPolicySupport.requireClubMember(club, playerId, "revoke club admin")
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
        ClubPolicySupport.ensureClubActive(club)
        ClubPolicySupport.requireActivePlayer(player, s"Player ${playerId.value} cannot receive club title")
        ClubPolicySupport.requireClubMember(club, playerId, "set internal title")
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
        ClubPolicySupport.ensureClubActive(club)
        ClubPolicySupport.requireActivePlayer(player, s"Player ${playerId.value} cannot clear club title")
        ClubPolicySupport.requireClubMember(club, playerId, "clear internal title")
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
