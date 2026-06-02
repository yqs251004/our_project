package riichinexus.microservices.auth.utils
import riichinexus.microservices.player.domain.functions.PlayerPersistenceFunctions

import java.sql.Connection
import java.util.NoSuchElementException

import cats.effect.IO
import riichinexus.system.api.ApiPlanContext
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
import riichinexus.microservices.auth.api.`private`.ResolveGuestSessionAuthPrivateAPIMessage
import riichinexus.microservices.auth.domain.functions.AccessPrincipalFunctions
import riichinexus.microservices.auth.domain.model.{AccessPrincipal, GuestAccessSession}
import riichinexus.microservices.player.api.GetPlayerAPIMessage
import riichinexus.microservices.player.domain.functions.PlayerPrincipalFunctions

final case class ResolveAccessPrincipal(
    playerId: PlayerId
):
  def resolve(connection: Connection): AccessPrincipal =
    PlayerPersistenceFunctions.findPlayer(connection, playerId)
      .map(PlayerPrincipalFunctions.asPrincipal)
      .getOrElse(throw NoSuchElementException(s"Player ${playerId.value} was not found"))

  def plan(connection: Connection): IO[AccessPrincipal] =
    IO.blocking(resolve(connection))

final case class ResolveGuestAccessPrincipal(
    sessionId: GuestSessionId
):
  def plan(context: ApiPlanContext): IO[AccessPrincipal] =
    resolveGuestSession(context).map(AccessPrincipalFunctions.guest)

  def resolveGuestSession(context: ApiPlanContext): IO[GuestAccessSession] =
    ResolveGuestSessionAuthPrivateAPIMessage(sessionId).plan(context)

final case class ResolveRequestActor(
    guestSessionId: Option[GuestSessionId],
    operatorId: Option[PlayerId]
):
  def plan(context: ApiPlanContext): IO[AccessPrincipal] =
    if guestSessionId.nonEmpty && operatorId.nonEmpty then
      IO.raiseError(IllegalArgumentException("guestSessionId and operatorId cannot be provided together"))
    else
      guestSessionId match
        case Some(sessionId) => ResolveGuestAccessPrincipal(sessionId).plan(context)
        case None =>
          operatorId match
            case Some(playerId) => ResolveAccessPrincipal(playerId).plan(context.connection)
            case None           => IO.pure(AccessPrincipalFunctions.guest())
