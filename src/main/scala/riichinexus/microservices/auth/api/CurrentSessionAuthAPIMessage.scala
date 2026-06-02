package riichinexus.microservices.auth.api
import riichinexus.microservices.player.domain.functions.PlayerPersistenceFunctions

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
import riichinexus.microservices.auth.objects.Role
import riichinexus.microservices.auth.objects.SessionPrincipalKind
import riichinexus.microservices.auth.objects.apiTypes.{
  CurrentSessionGuestSessionView,
  CurrentSessionPlayerView,
  CurrentSessionRoleFlags,
  CurrentSessionView
}
import riichinexus.microservices.auth.utils.ResolveGuestAccessPrincipal
import riichinexus.microservices.player.api.GetPlayerAPIMessage
import riichinexus.microservices.player.domain.Player
import riichinexus.system.json.JsonCodecs.given
import upickle.default.*

final case class CurrentSessionAuthAPIMessage(
    operatorId: Option[String] = None,
    guestSessionId: Option[String] = None
) extends APIMessage[CurrentSessionView] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[CurrentSessionView] =
    for
      input <- IO.blocking(resolveInput)
      session <- resolveCurrentSession(context, input)
    yield session

  private def resolveInput: CurrentSessionInput =
    CurrentSessionInput(
      operatorId = operatorId.filter(_.nonEmpty).map(PlayerId(_)),
      guestSessionId = guestSessionId.filter(_.nonEmpty).map(GuestSessionId(_))
    )

  private def resolveCurrentSession(
      context: ApiPlanContext,
      input: CurrentSessionInput
  ): IO[CurrentSessionView] =
    if input.operatorId.nonEmpty && input.guestSessionId.nonEmpty then
      IO.raiseError(IllegalArgumentException("guestSessionId and operatorId cannot be provided together"))
    else
      input.operatorId match
        case Some(playerId) =>
          IO.blocking(
            PlayerPersistenceFunctions.findPlayer(context.connection, playerId)
              .map(registeredPlayerView)
              .getOrElse(throw NoSuchElementException(s"Player ${playerId.value} was not found"))
          )
        case None =>
          input.guestSessionId match
            case Some(sessionId) =>
              ResolveGuestAccessPrincipal(sessionId).resolveGuestSession(context)
                .map(guestSessionView)
            case None =>
              IO.pure(anonymousView)

  private def registeredRoleFlags(player: Player): CurrentSessionRoleFlags =
    CurrentSessionRoleFlags(
      isGuest = false,
      isRegisteredPlayer = true,
      isClubAdmin = player.roleGrants.exists(_.role == Role.ClubAdmin),
      isTournamentAdmin = player.roleGrants.exists(_.role == Role.TournamentAdmin),
      isSuperAdmin = player.roleGrants.exists(_.role == Role.SuperAdmin)
    )

  private def registeredPlayerView(player: Player): CurrentSessionView =
    CurrentSessionView(
      principalKind = SessionPrincipalKind.RegisteredPlayer,
      principalId = player.id.value,
      displayName = player.nickname,
      authenticated = true,
      roles = registeredRoleFlags(player),
      player = Some(
        CurrentSessionPlayerView(
          id = player.id.value,
          userId = player.userId,
          nickname = player.nickname
        )
      )
    )

  private def guestSessionView(session: riichinexus.microservices.auth.domain.model.GuestAccessSession): CurrentSessionView =
    CurrentSessionView(
      principalKind = SessionPrincipalKind.Guest,
      principalId = session.id.value,
      displayName = session.displayName,
      authenticated = true,
      roles = guestRoleFlags,
      guestSession = Some(
        CurrentSessionGuestSessionView(
          id = session.id.value,
          displayName = session.displayName
        )
      )
    )

  private def anonymousView: CurrentSessionView =
    CurrentSessionView(
      principalKind = SessionPrincipalKind.Anonymous,
      principalId = "anonymous",
      displayName = "Guest",
      authenticated = false,
      roles = guestRoleFlags
    )

  private def guestRoleFlags: CurrentSessionRoleFlags =
    CurrentSessionRoleFlags(
      isGuest = true,
      isRegisteredPlayer = false,
      isClubAdmin = false,
      isTournamentAdmin = false,
      isSuperAdmin = false
    )

  private final case class CurrentSessionInput(
      operatorId: Option[PlayerId],
      guestSessionId: Option[GuestSessionId]
  )
