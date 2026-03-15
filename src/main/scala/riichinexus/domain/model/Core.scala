package riichinexus.domain.model

import java.time.Instant
import java.util.UUID

final case class PlayerId(value: String) derives CanEqual
final case class ClubId(value: String) derives CanEqual
final case class TournamentId(value: String) derives CanEqual
final case class TournamentStageId(value: String) derives CanEqual
final case class TableId(value: String) derives CanEqual
final case class PaifuId(value: String) derives CanEqual
final case class MatchRecordId(value: String) derives CanEqual
final case class AppealTicketId(value: String) derives CanEqual
final case class MembershipApplicationId(value: String) derives CanEqual
final case class LineupSubmissionId(value: String) derives CanEqual
final case class GuestSessionId(value: String) derives CanEqual
final case class SettlementSnapshotId(value: String) derives CanEqual
final case class AuditEventId(value: String) derives CanEqual

object IdGenerator:
  private def nextId(prefix: String): String =
    s"$prefix-${UUID.randomUUID().toString.take(8)}"

  def playerId(): PlayerId = PlayerId(nextId("player"))
  def clubId(): ClubId = ClubId(nextId("club"))
  def tournamentId(): TournamentId = TournamentId(nextId("tournament"))
  def stageId(): TournamentStageId = TournamentStageId(nextId("stage"))
  def tableId(): TableId = TableId(nextId("table"))
  def paifuId(): PaifuId = PaifuId(nextId("paifu"))
  def matchRecordId(): MatchRecordId = MatchRecordId(nextId("record"))
  def appealTicketId(): AppealTicketId = AppealTicketId(nextId("appeal"))
  def membershipApplicationId(): MembershipApplicationId =
    MembershipApplicationId(nextId("membership"))
  def lineupSubmissionId(): LineupSubmissionId = LineupSubmissionId(nextId("lineup"))
  def guestSessionId(): GuestSessionId = GuestSessionId(nextId("guest"))
  def settlementSnapshotId(): SettlementSnapshotId = SettlementSnapshotId(nextId("settlement"))
  def auditEventId(): AuditEventId = AuditEventId(nextId("audit"))

enum RoleKind derives CanEqual:
  case Guest
  case RegisteredPlayer
  case ClubAdmin
  case TournamentAdmin
  case SuperAdmin

enum Permission derives CanEqual:
  case ViewPublicSchedule
  case ViewClubDirectory
  case ViewPublicLeaderboard
  case ViewOwnDashboard
  case ViewClubDashboard
  case SubmitClubApplication
  case WithdrawClubApplication
  case ManageClubMembership
  case ManageClubOperations
  case SetClubTitle
  case AssignClubAdmin
  case SubmitTournamentLineup
  case ManageTournamentStages
  case ConfigureTournamentRules
  case ResetTableState
  case ManageTableSeatState
  case FileAppealTicket
  case ResolveAppeal
  case ManageGlobalDictionary
  case BanRegisteredPlayer
  case DissolveClub
  case AssignTournamentAdmin
  case ViewAuditTrail

final case class RoleGrant(
    role: RoleKind,
    grantedAt: Instant,
    grantedBy: Option[PlayerId] = None,
    clubId: Option[ClubId] = None,
    tournamentId: Option[TournamentId] = None
) derives CanEqual:
  require(
    role match
      case RoleKind.Guest | RoleKind.RegisteredPlayer | RoleKind.SuperAdmin =>
        clubId.isEmpty && tournamentId.isEmpty
      case RoleKind.ClubAdmin =>
        clubId.nonEmpty && tournamentId.isEmpty
      case RoleKind.TournamentAdmin =>
        tournamentId.nonEmpty && clubId.isEmpty,
    s"Invalid scope for role $role"
  )

  def appliesToClub(targetClubId: ClubId): Boolean =
    role == RoleKind.SuperAdmin || clubId.contains(targetClubId)

  def appliesToTournament(targetTournamentId: TournamentId): Boolean =
    role == RoleKind.SuperAdmin || tournamentId.contains(targetTournamentId)

object RoleGrant:
  def guest(at: Instant = Instant.now()): RoleGrant =
    RoleGrant(RoleKind.Guest, grantedAt = at)

  def registered(at: Instant): RoleGrant =
    RoleGrant(RoleKind.RegisteredPlayer, grantedAt = at)

  def clubAdmin(
      clubId: ClubId,
      grantedAt: Instant = Instant.now(),
      grantedBy: Option[PlayerId] = None
  ): RoleGrant =
    RoleGrant(
      role = RoleKind.ClubAdmin,
      grantedAt = grantedAt,
      grantedBy = grantedBy,
      clubId = Some(clubId)
    )

  def tournamentAdmin(
      tournamentId: TournamentId,
      grantedAt: Instant = Instant.now(),
      grantedBy: Option[PlayerId] = None
  ): RoleGrant =
    RoleGrant(
      role = RoleKind.TournamentAdmin,
      grantedAt = grantedAt,
      grantedBy = grantedBy,
      tournamentId = Some(tournamentId)
    )

  def superAdmin(
      grantedAt: Instant = Instant.now(),
      grantedBy: Option[PlayerId] = None
  ): RoleGrant =
    RoleGrant(
      role = RoleKind.SuperAdmin,
      grantedAt = grantedAt,
      grantedBy = grantedBy
    )

final case class GuestAccessSession(
    id: GuestSessionId,
    createdAt: Instant,
    displayName: String = "guest"
) derives CanEqual

final case class AccessPrincipal(
    principalId: String,
    displayName: String,
    playerId: Option[PlayerId],
    roleGrants: Vector[RoleGrant]
) derives CanEqual:
  def isGuest: Boolean =
    playerId.isEmpty

  def isSuperAdmin: Boolean =
    roleGrants.exists(_.role == RoleKind.SuperAdmin)

  def hasRole(role: RoleKind): Boolean =
    isSuperAdmin || roleGrants.exists(_.role == role)

  def hasClubRole(role: RoleKind, clubId: ClubId): Boolean =
    isSuperAdmin || roleGrants.exists(grant => grant.role == role && grant.clubId.contains(clubId))

  def hasTournamentRole(role: RoleKind, tournamentId: TournamentId): Boolean =
    isSuperAdmin || roleGrants.exists(grant =>
      grant.role == role && grant.tournamentId.contains(tournamentId)
    )

object AccessPrincipal:
  def guest(session: GuestAccessSession = GuestAccessSession(IdGenerator.guestSessionId(), Instant.now())): AccessPrincipal =
    AccessPrincipal(
      principalId = session.id.value,
      displayName = session.displayName,
      playerId = None,
      roleGrants = Vector(RoleGrant.guest(session.createdAt))
    )

  def system: AccessPrincipal =
    AccessPrincipal(
      principalId = "system-bootstrap",
      displayName = "system",
      playerId = None,
      roleGrants = Vector(RoleGrant.superAdmin(Instant.EPOCH, None))
    )

enum RankPlatform derives CanEqual:
  case Tenhou
  case MahjongSoul
  case Custom

final case class RankSnapshot(
    platform: RankPlatform,
    tier: String,
    stars: Option[Int] = None
) derives CanEqual

enum PlayerStatus derives CanEqual:
  case Active
  case Suspended
  case Banned

final case class Player(
    id: PlayerId,
    userId: String,
    nickname: String,
    registeredAt: Instant,
    currentRank: RankSnapshot,
    elo: Int,
    clubId: Option[ClubId] = None,
    affiliatedClubIds: Vector[ClubId] = Vector.empty,
    status: PlayerStatus = PlayerStatus.Active,
    roleGrants: Vector[RoleGrant] = Vector.empty,
    bannedReason: Option[String] = None
) derives CanEqual:
  def boundClubIds: Vector[ClubId] =
    (clubId.toVector ++ affiliatedClubIds).distinct

  def effectiveRoleGrants: Vector[RoleGrant] =
    if roleGrants.exists(_.role == RoleKind.RegisteredPlayer) then roleGrants
    else RoleGrant.registered(registeredAt) +: roleGrants

  def asPrincipal: AccessPrincipal =
    AccessPrincipal(
      principalId = id.value,
      displayName = nickname,
      playerId = Some(id),
      roleGrants = effectiveRoleGrants
    )

  def joinClub(newClubId: ClubId): Player =
    val updatedBoundClubs = (boundClubIds :+ newClubId).distinct
    val nextPrimaryClubId = clubId.orElse(Some(newClubId))
    copy(
      clubId = nextPrimaryClubId,
      affiliatedClubIds = updatedBoundClubs.filterNot(nextPrimaryClubId.contains)
    )

  def leaveClub(existingClubId: ClubId): Player =
    val remaining = boundClubIds.filterNot(_ == existingClubId)
    copy(
      clubId = remaining.headOption,
      affiliatedClubIds = remaining.drop(1)
    )

  def leaveClub: Player =
    clubId match
      case Some(primaryClubId) => leaveClub(primaryClubId)
      case None                => copy(affiliatedClubIds = Vector.empty)

  def updateRank(rank: RankSnapshot): Player =
    copy(currentRank = rank)

  def applyElo(delta: Int): Player =
    copy(elo = elo + delta)

  def grantRole(grant: RoleGrant): Player =
    val normalized = roleGrants.filterNot(existing =>
      existing.role == grant.role &&
        existing.clubId == grant.clubId &&
        existing.tournamentId == grant.tournamentId
    )
    copy(roleGrants = (normalized :+ grant).sortBy(_.grantedAt.toEpochMilli))

  def revokeClubAdmin(clubId: ClubId): Player =
    copy(roleGrants = roleGrants.filterNot(grant =>
      grant.role == RoleKind.ClubAdmin && grant.clubId.contains(clubId)
    ))

  def revokeTournamentAdmin(tournamentId: TournamentId): Player =
    copy(roleGrants = roleGrants.filterNot(grant =>
      grant.role == RoleKind.TournamentAdmin && grant.tournamentId.contains(tournamentId)
    ))

  def ban(reason: String): Player =
    copy(
      status = PlayerStatus.Banned,
      bannedReason = Some(reason)
    )

final case class ClubHonor(
    title: String,
    achievedAt: Instant,
    note: Option[String] = None
) derives CanEqual:
  require(title.trim.nonEmpty, "Club honor title cannot be empty")

enum ClubMembershipApplicationStatus derives CanEqual:
  case Pending
  case Approved
  case Rejected
  case Withdrawn

final case class ClubMembershipApplication(
    id: MembershipApplicationId,
    applicantUserId: Option[String],
    displayName: String,
    submittedAt: Instant,
    message: Option[String] = None,
    status: ClubMembershipApplicationStatus = ClubMembershipApplicationStatus.Pending,
    reviewedBy: Option[PlayerId] = None,
    reviewedAt: Option[Instant] = None,
    reviewNote: Option[String] = None,
    withdrawnByPrincipalId: Option[String] = None
) derives CanEqual:
  def isPending: Boolean =
    status == ClubMembershipApplicationStatus.Pending

  def approve(by: PlayerId, at: Instant, note: Option[String] = None): ClubMembershipApplication =
    require(isPending, "Only pending applications can be approved")
    copy(
      status = ClubMembershipApplicationStatus.Approved,
      reviewedBy = Some(by),
      reviewedAt = Some(at),
      reviewNote = note
    )

  def reject(by: PlayerId, at: Instant, note: Option[String] = None): ClubMembershipApplication =
    require(isPending, "Only pending applications can be rejected")
    copy(
      status = ClubMembershipApplicationStatus.Rejected,
      reviewedBy = Some(by),
      reviewedAt = Some(at),
      reviewNote = note
    )

  def withdraw(
      byPrincipalId: String,
      at: Instant,
      note: Option[String] = None
  ): ClubMembershipApplication =
    require(isPending, "Only pending applications can be withdrawn")
    copy(
      status = ClubMembershipApplicationStatus.Withdrawn,
      reviewedAt = Some(at),
      reviewNote = note,
      withdrawnByPrincipalId = Some(byPrincipalId)
    )

final case class ClubRankNode(
    code: String,
    label: String,
    minimumContribution: Int,
    privileges: Vector[String] = Vector.empty
) derives CanEqual

final case class ClubTitleAssignment(
    playerId: PlayerId,
    title: String,
    assignedBy: PlayerId,
    assignedAt: Instant,
    note: Option[String] = None
) derives CanEqual

enum ClubRelationKind derives CanEqual:
  case Alliance
  case Rivalry
  case Neutral

final case class ClubRelation(
    targetClubId: ClubId,
    relation: ClubRelationKind,
    updatedAt: Instant,
    note: Option[String] = None
) derives CanEqual

final case class GlobalDictionaryEntry(
    key: String,
    value: String,
    updatedAt: Instant,
    updatedBy: PlayerId,
    note: Option[String] = None
) derives CanEqual

final case class AuditEventEntry(
    id: AuditEventId,
    aggregateType: String,
    aggregateId: String,
    eventType: String,
    occurredAt: Instant,
    actorId: Option[PlayerId] = None,
    details: Map[String, String] = Map.empty,
    note: Option[String] = None
) derives CanEqual

final case class Club(
    id: ClubId,
    name: String,
    creator: PlayerId,
    createdAt: Instant,
    members: Vector[PlayerId] = Vector.empty,
    admins: Vector[PlayerId] = Vector.empty,
    totalPoints: Int = 0,
    treasuryBalance: Long = 0L,
    pointPool: Int = 0,
    rankTree: Vector[ClubRankNode] = Club.defaultRankTree,
    titleAssignments: Vector[ClubTitleAssignment] = Vector.empty,
    powerRating: Double = 0.0,
    honors: Vector[ClubHonor] = Vector.empty,
    relations: Vector[ClubRelation] = Vector.empty,
    membershipApplications: Vector[ClubMembershipApplication] = Vector.empty,
    dissolvedAt: Option[Instant] = None,
    dissolvedBy: Option[PlayerId] = None
) derives CanEqual:
  def addMember(playerId: PlayerId): Club =
    if members.contains(playerId) then this
    else copy(members = members :+ playerId)

  def removeMember(playerId: PlayerId): Club =
    copy(
      members = members.filterNot(_ == playerId),
      admins = admins.filterNot(_ == playerId),
      titleAssignments = titleAssignments.filterNot(_.playerId == playerId)
    )

  def addPoints(points: Int): Club =
    copy(
      totalPoints = totalPoints + points,
      pointPool = pointPool + points
    )

  def adjustTreasury(delta: Long): Club =
    require(treasuryBalance + delta >= 0L, "Club treasury balance cannot be negative")
    copy(treasuryBalance = treasuryBalance + delta)

  def adjustPointPool(delta: Int): Club =
    require(pointPool + delta >= 0, "Club point pool cannot be negative")
    copy(pointPool = pointPool + delta)

  def addHonor(honor: ClubHonor): Club =
    val normalizedTitle = honor.title.trim.toLowerCase
    copy(
      honors =
        honors.filterNot(_.title.trim.toLowerCase == normalizedTitle) :+ honor
    )

  def removeHonor(title: String): Club =
    val normalizedTitle = title.trim.toLowerCase
    copy(honors = honors.filterNot(_.title.trim.toLowerCase == normalizedTitle))

  def grantAdmin(playerId: PlayerId): Club =
    copy(admins = (admins :+ playerId).distinct)

  def revokeAdmin(playerId: PlayerId): Club =
    copy(admins = admins.filterNot(_ == playerId))

  def setInternalTitle(assignment: ClubTitleAssignment): Club =
    copy(
      titleAssignments =
        titleAssignments.filterNot(_.playerId == assignment.playerId) :+ assignment
    )

  def clearInternalTitle(playerId: PlayerId): Club =
    copy(
      titleAssignments = titleAssignments.filterNot(_.playerId == playerId)
    )

  def submitApplication(application: ClubMembershipApplication): Club =
    copy(
      membershipApplications =
        membershipApplications.filterNot(_.id == application.id) :+ application
    )

  def reviewApplication(
      applicationId: MembershipApplicationId,
      review: ClubMembershipApplication => ClubMembershipApplication
  ): Club =
    copy(
      membershipApplications = membershipApplications.map { application =>
        if application.id == applicationId then review(application) else application
      }
    )

  def findApplication(applicationId: MembershipApplicationId): Option[ClubMembershipApplication] =
    membershipApplications.find(_.id == applicationId)

  def updatePowerRating(rating: Double): Club =
    copy(powerRating = rating)

  def updateRankTree(nodes: Vector[ClubRankNode]): Club =
    require(nodes.nonEmpty, "Club rank tree cannot be empty")
    require(
      nodes.map(_.code.trim.toLowerCase).distinct.size == nodes.size,
      "Club rank node codes must be unique"
    )
    require(
      nodes.map(_.label.trim.toLowerCase).distinct.size == nodes.size,
      "Club rank node labels must be unique"
    )
    require(
      nodes.forall(node => node.code.trim.nonEmpty && node.label.trim.nonEmpty),
      "Club rank node code and label cannot be empty"
    )
    require(
      nodes.forall(_.minimumContribution >= 0),
      "Club rank node minimum contribution cannot be negative"
    )
    val normalized = nodes.sortBy(node => (node.minimumContribution, node.code.trim.toLowerCase))
    require(
      normalized.head.minimumContribution == 0,
      "Club rank tree must start at minimum contribution 0"
    )
    copy(rankTree = normalized)

  def upsertRelation(relation: ClubRelation): Club =
    copy(
      relations = relations.filterNot(_.targetClubId == relation.targetClubId) :+ relation
    )

  def removeRelation(targetClubId: ClubId): Club =
    copy(relations = relations.filterNot(_.targetClubId == targetClubId))

  def dissolve(by: PlayerId, at: Instant): Club =
    copy(
      dissolvedAt = Some(at),
      dissolvedBy = Some(by)
    )

object Club:
  val defaultRankTree: Vector[ClubRankNode] =
    Vector(
      ClubRankNode("rookie", "Rookie", minimumContribution = 0),
      ClubRankNode("member", "Member", minimumContribution = 500),
      ClubRankNode("core", "Core", minimumContribution = 1500),
      ClubRankNode("ace", "Ace", minimumContribution = 3000)
    )
