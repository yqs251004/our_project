package riichinexus.microservices.auth.api.`private`

import java.time.Instant
import java.util.NoSuchElementException

import cats.effect.IO
import riichinexus.system.api.{APIMessage, ApiPlanContext}
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
import riichinexus.system.json.JsonCodecs.given
import riichinexus.microservices.auth.domain.functions.GuestAccessSessionFunctions
import riichinexus.microservices.auth.domain.model.GuestAccessSession
import riichinexus.microservices.auth.tables.guestsession.GuestSessionTable
import upickle.default.*

final case class ResolveGuestSessionAuthPrivateAPIMessage(
    sessionId: GuestSessionId
) extends APIMessage[GuestAccessSession] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[GuestAccessSession] =
    for
      seenAt <- IO.realTimeInstant
      session <- IO.blocking {
        {
          touchGuestSession(context.connection, seenAt)
        }
      }
    yield session

  private def touchGuestSession(connection: java.sql.Connection, seenAt: Instant): GuestAccessSession =
    val session = GuestSessionTable
      .findById(connection, sessionId)
      .getOrElse(throw NoSuchElementException(s"Guest session ${sessionId.value} was not found"))
    ensureCanAuthenticate(session, seenAt)
    GuestSessionTable.save(connection, GuestAccessSessionFunctions.touch(session, seenAt))

  private def ensureCanAuthenticate(session: GuestAccessSession, seenAt: Instant): Unit =
    require(GuestAccessSessionFunctions.canAuthenticate(session, seenAt), inactiveSessionMessage(session, seenAt))

  private def inactiveSessionMessage(session: GuestAccessSession, at: Instant): String =
    if GuestAccessSessionFunctions.isRevoked(session) then
      s"Guest session ${session.id.value} has been revoked"
    else if GuestAccessSessionFunctions.isUpgraded(session) then
      s"Guest session ${session.id.value} has already been upgraded to player access"
    else if GuestAccessSessionFunctions.isExpired(session, at) then
      s"Guest session ${session.id.value} expired at ${session.expiresAt}"
    else
      s"Guest session ${session.id.value} cannot be used for authentication"
