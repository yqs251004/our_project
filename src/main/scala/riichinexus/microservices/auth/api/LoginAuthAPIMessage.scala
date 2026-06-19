package riichinexus.microservices.auth.api
import riichinexus.microservices.player.api.`private`.ResolvePlayerPrivateAPIMessage

import java.time.Instant

import cats.effect.IO
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.microservices.player.objects.`private`.PlayerPrivateView
import riichinexus.microservices.auth.domain.AuthenticationFailure
import riichinexus.microservices.auth.domain.functions.{AccountCredentialFunctions, AuthenticatedSessionFunctions, PasswordHashFunctions}
import riichinexus.microservices.auth.domain.model.{AccountCredential, AuthenticatedSession}
import riichinexus.microservices.auth.objects.Role
import riichinexus.microservices.auth.objects.apiTypes.{AuthSuccessView, CurrentSessionRoleFlags}
import riichinexus.microservices.auth.tables.accountcredential.AccountCredentialTable
import riichinexus.microservices.auth.tables.authenticatedsession.AuthenticatedSessionTable
import upickle.default.ReadWriter

/** 使用账号密码登录。 */
final case class LoginAuthAPIMessage(
    username: String,
    password: String
) extends APIMessage[AuthSuccessView] derives ReadWriter:

  private val SessionTtl = java.time.Duration.ofDays(30)

  override def plan(context: ApiPlanContext): IO[AuthSuccessView] =
    for
      loginAt <- IO.realTimeInstant
      command <- IO.blocking(buildCommand(loginAt))
      credential <- authenticateCredential(context, command)
      player <- resolveCredentialPlayer(context, credential)
      _ <- IO.blocking(ensureActivePlayer(player))
      session <- createSession(context, command, credential)
    yield authSuccessView(credential.username, player, session)

  private def buildCommand(loginAt: Instant): LoginCommand =
    LoginCommand(AccountCredentialFunctions.normalizeUsername(username), password, loginAt)

  private def authenticateCredential(
      context: ApiPlanContext,
      command: LoginCommand
  ): IO[AccountCredential] =
    IO.blocking {
      require(command.password.nonEmpty, "Password is required")
      val credential = AccountCredentialTable.findByUsername(context.connection, command.username)
        .getOrElse(throw AuthenticationFailure("Invalid username or password", "invalid_credentials"))
      if !PasswordHashFunctions.verify(command.password, credential) then
        throw AuthenticationFailure("Invalid username or password", "invalid_credentials")
      credential
    }

  private def resolveCredentialPlayer(
      context: ApiPlanContext,
      credential: AccountCredential
  ): IO[PlayerPrivateView] =
    ResolvePlayerPrivateAPIMessage(credential.playerId).plan(context).map(
      _.getOrElse(throw AuthenticationFailure(s"Player ${credential.playerId.value} was not found", "invalid_credentials"))
    )

  private def createSession(
      context: ApiPlanContext,
      command: LoginCommand,
      credential: AccountCredential
  ): IO[AuthenticatedSession] =
    IO.blocking(
      AuthenticatedSessionTable.save(
        context.connection,
        AuthenticatedSessionFunctions.create(
          username = credential.username,
          playerId = credential.playerId,
          createdAt = command.loginAt,
          ttl = SessionTtl
        )
      )
    )

  private def ensureActivePlayer(player: PlayerPrivateView): Unit =
    if !player.active then
      throw AuthenticationFailure(
        s"Player ${player.id.value} is not active",
        "inactive_account"
      )

  private def registeredRoleFlags(player: PlayerPrivateView): CurrentSessionRoleFlags =
    CurrentSessionRoleFlags(
      isGuest = false,
      isRegisteredPlayer = true,
      isClubAdmin = player.roleGrants.exists(_.role == Role.ClubAdmin),
      isTournamentAdmin = player.roleGrants.exists(_.role == Role.TournamentAdmin),
      isSuperAdmin = player.roleGrants.exists(_.role == Role.SuperAdmin)
    )

  private def authSuccessView(
      username: String,
      player: PlayerPrivateView,
      session: AuthenticatedSession
  ): AuthSuccessView =
    AuthSuccessView(
      userId = player.id.value,
      username = username,
      displayName = player.nickname,
      token = session.token,
      roles = registeredRoleFlags(player)
    )

  private final case class LoginCommand(
      username: String,
      password: String,
      loginAt: Instant
  )
