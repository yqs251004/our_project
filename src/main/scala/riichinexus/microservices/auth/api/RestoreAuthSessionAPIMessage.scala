package riichinexus.microservices.auth.api

import riichinexus.microservices.player.api.`private`.ResolvePlayerPrivateAPIMessage

import java.time.Instant

import cats.effect.IO
import riichinexus.system.api.{APIWithTokenMessage, ApiPlanContext}
import riichinexus.microservices.auth.domain.AuthenticationFailure
import riichinexus.microservices.auth.domain.functions.AuthenticatedSessionFunctions
import riichinexus.microservices.auth.domain.model.AuthenticatedSession
import riichinexus.microservices.auth.objects.Role
import riichinexus.microservices.auth.objects.apiTypes.{AuthSessionView, CurrentSessionRoleFlags}
import riichinexus.microservices.player.objects.`private`.PlayerPrivateView
import riichinexus.microservices.auth.tables.authenticatedsession.AuthenticatedSessionTable
/** 从 token 恢复登录会话。 */
final case class RestoreAuthSessionAPIMessage() extends APIWithTokenMessage[AuthSessionView]:

  override def plan(context: ApiPlanContext): IO[AuthSessionView] =
    for
      token <- IO.blocking(ApiPlanContext.requireBearerToken(context))
      asOf <- IO.realTimeInstant
      command = RestoreSessionCommand(token, asOf)
      result <- restoreSession(context, command)
    yield AuthSessionView(
      userId = result.player.id.value,
      username = result.session.username,
      displayName = result.player.nickname,
      authenticated = true,
      roles = registeredRoleFlags(result.player)
    )

  private def restoreSession(
      context: ApiPlanContext,
      command: RestoreSessionCommand
  ): IO[RestoreSessionResult] =
    val connection = context.connection
    for
      touched <- IO.blocking {
        val session = AuthenticatedSessionTable.findByToken(connection, command.token)
          .getOrElse(throw AuthenticationFailure("Session is invalid or expired", "invalid_session"))
        if !AuthenticatedSessionFunctions.canAuthenticate(session, command.asOf) then
          throw AuthenticationFailure("Session is invalid or expired", "invalid_session")
        AuthenticatedSessionTable.save(connection, AuthenticatedSessionFunctions.touch(session, command.asOf))
      }
      player <- ResolvePlayerPrivateAPIMessage(touched.playerId).plan(context).map(
        _.getOrElse(throw AuthenticationFailure(s"Player ${touched.playerId.value} was not found", "invalid_session"))
      )
      _ <- IO.blocking {
        if !player.active then
          throw AuthenticationFailure(s"Player ${player.id.value} is not active", "inactive_account")
      }
    yield RestoreSessionResult(touched, player)

  /** 根据 bearer token 恢复正式账号会话时使用的内部命令。 */
  private final case class RestoreSessionCommand(
      token: String,
      asOf: Instant
  )

  /** 恢复会话成功后得到的刷新后会话和玩家视图。 */
  private final case class RestoreSessionResult(
      session: AuthenticatedSession,
      player: PlayerPrivateView
  )

  private def registeredRoleFlags(player: PlayerPrivateView): CurrentSessionRoleFlags =
    CurrentSessionRoleFlags(
      isGuest = false,
      isRegisteredPlayer = true,
      isClubAdmin = player.roleGrants.exists(_.role == Role.ClubAdmin),
      isTournamentAdmin = player.roleGrants.exists(_.role == Role.TournamentAdmin),
      isSuperAdmin = player.roleGrants.exists(_.role == Role.SuperAdmin)
    )
