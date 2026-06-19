package riichinexus.microservices.auth.domain.functions

import java.time.{Duration, Instant}

import riichinexus.microservices.player.domain.functions.PlayerIdGenerator
import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.microservices.club.domain.functions.ClubIdGenerator
import riichinexus.microservices.club.objects.clubmanagement.ClubId
import riichinexus.microservices.club.objects.membershipmanagement.MembershipApplicationId
import riichinexus.microservices.tournament.domain.functions.TournamentIdGenerator
import riichinexus.microservices.tournament.objects.lineupmanagement.LineupSubmissionId
import riichinexus.microservices.tournament.objects.paifumanagement.PaifuId
import riichinexus.microservices.tournament.objects.recordmanagement.MatchRecordId
import riichinexus.microservices.tournament.objects.settlementmanagement.SettlementSnapshotId
import riichinexus.microservices.tournament.objects.tablemanagement.TableId
import riichinexus.microservices.tournament.objects.tournamentmanagement.{TournamentId, TournamentStageId}
import riichinexus.microservices.tournament.appeal.domain.functions.AppealIdGenerator
import riichinexus.microservices.tournament.appeal.objects.ticketmanagement.AppealTicketId
import riichinexus.microservices.auth.domain.functions.AuthIdGenerator
import riichinexus.microservices.auth.objects.sessionmanagement.GuestSessionId
import riichinexus.microservices.audit.domain.functions.AuditIdGenerator
import riichinexus.microservices.audit.domain.auditevent.AuditEventId
import riichinexus.microservices.opsanalytics.domain.functions.OpsAnalyticsIdGenerator
import riichinexus.microservices.opsanalytics.objects.advancedstats.AdvancedStatsRecomputeTaskId
import riichinexus.microservices.auth.domain.model.GuestAccessSession

private[auth] object GuestAccessSessionFunctions:
  private val DefaultTtl: Duration = Duration.ofDays(30)

  def isRevoked(session: GuestAccessSession): Boolean =
    session.revokedAt.nonEmpty

  def isExpired(session: GuestAccessSession, asOf: Instant = Instant.now()): Boolean =
    !session.expiresAt.isAfter(asOf)

  def isUpgraded(session: GuestAccessSession): Boolean =
    session.upgradedToPlayerId.nonEmpty

  def canAuthenticate(session: GuestAccessSession, asOf: Instant = Instant.now()): Boolean =
    !isRevoked(session) && !isExpired(session, asOf) && !isUpgraded(session)

  def touch(session: GuestAccessSession, at: Instant): GuestAccessSession =
    session.copy(lastSeenAt = Some(latestSeenAt(session.lastSeenAt, at)))

  def revoke(session: GuestAccessSession, reason: String, at: Instant): GuestAccessSession =
    val normalizedReason = reason.trim
    require(normalizedReason.nonEmpty, "Guest session revocation reason cannot be empty")
    session.copy(
      revokedAt = Some(at),
      revokedReason = Some(normalizedReason)
    )

  def upgrade(session: GuestAccessSession, playerId: PlayerId, at: Instant): GuestAccessSession =
    session.copy(
      lastSeenAt = Some(latestSeenAt(session.lastSeenAt, at)),
      upgradedToPlayerId = Some(playerId)
    )

  def create(
      id: GuestSessionId = AuthIdGenerator.guestSessionId(),
      createdAt: Instant = Instant.now(),
      displayName: String = "guest",
      ttl: Duration = DefaultTtl,
      deviceFingerprint: Option[String] = None
  ): GuestAccessSession =
    require(!ttl.isNegative && !ttl.isZero, "Guest session TTL must be positive")
    GuestAccessSession(
      id = id,
      createdAt = createdAt,
      displayName = displayName.trim,
      expiresAt = createdAt.plus(ttl),
      deviceFingerprint = deviceFingerprint.map(_.trim).filter(_.nonEmpty)
    )

  def ephemeral(createdAt: Instant = Instant.now()): GuestAccessSession =
    create(createdAt = createdAt, ttl = Duration.ofMinutes(5))

  def validate(session: GuestAccessSession): Unit =
    require(session.displayName.trim.nonEmpty, "Guest session display name cannot be empty")
    require(!session.expiresAt.isBefore(session.createdAt), "Guest session expiry cannot be earlier than creation")

  private def latestSeenAt(current: Option[Instant], at: Instant): Instant =
    current match
      case Some(existing) if existing.isAfter(at) => existing
      case _                                      => at
