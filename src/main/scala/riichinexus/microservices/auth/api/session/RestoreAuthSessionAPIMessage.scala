package riichinexus.microservices.auth.api.session

import riichinexus.microservices.player.api.`private`.ResolvePlayerPrivateAPIMessage

import java.time.Instant

import cats.effect.IO
import riichinexus.system.api.{APIWithTokenMessage, ApiPlanContext}
import riichinexus.microservices.auth.domain.account.model.AuthenticationFailure
import riichinexus.microservices.auth.domain.session.functions.AuthenticatedSessionFunctions
import riichinexus.microservices.auth.domain.session.model.AuthenticatedSession
import riichinexus.microservices.auth.objects.authorization.Role
import riichinexus.microservices.auth.objects.session.AuthSessionView
import riichinexus.microservices.auth.objects.session.CurrentSessionRoleFlags
import riichinexus.microservices.player.objects.`private`.PlayerPrivateView
import riichinexus.microservices.auth.tables.authenticatedsession.AuthenticatedSessionTable
/** 从 token 恢复登录会话。 */
final case class RestoreAuthSessionAPIMessage() extends APIWithTokenMessage[AuthSessionView]:

  override def plan(context: ApiPlanContext): IO[AuthSessionView] =
    for
      token <- IO.blocking(ApiPlanContext.requireBearerToken(context))
      asOf <- IO.realTimeInstant
      restored <- restoreSession(context, token, asOf)
      (session, player) = restored
    yield AuthSessionView(
      userId = player.id.value,
      username = session.username,
      displayName = player.nickname,
      authenticated = true,
      roles = registeredRoleFlags(player)
    )

  private def restoreSession(
      context: ApiPlanContext,
      token: String,
      asOf: Instant
  ): IO[(AuthenticatedSession, PlayerPrivateView)] =
    val connection = context.connection
    for
      touched <- IO.blocking {
        val session = AuthenticatedSessionTable.findByToken(connection, token)
          .getOrElse(throw AuthenticationFailure("Session is invalid or expired", "invalid_session"))
        if !AuthenticatedSessionFunctions.canAuthenticate(session, asOf) then
          throw AuthenticationFailure("Session is invalid or expired", "invalid_session")
        AuthenticatedSessionTable.save(connection, AuthenticatedSessionFunctions.touch(session, asOf))
      }
      player <- ResolvePlayerPrivateAPIMessage(touched.playerId).plan(context).map(
        _.getOrElse(throw AuthenticationFailure(s"Player ${touched.playerId.value} was not found", "invalid_session"))
      )
      _ <- IO.blocking {
        if !player.active then
          throw AuthenticationFailure(s"Player ${player.id.value} is not active", "inactive_account")
      }
    yield (touched, player)

  private def registeredRoleFlags(player: PlayerPrivateView): CurrentSessionRoleFlags =
    CurrentSessionRoleFlags(
      isGuest = false,
      isRegisteredPlayer = true,
      isClubAdmin = player.roleGrants.exists(_.role == Role.ClubAdmin),
      isTournamentAdmin = player.roleGrants.exists(_.role == Role.TournamentAdmin),
      isSuperAdmin = player.roleGrants.exists(_.role == Role.SuperAdmin)
    )
