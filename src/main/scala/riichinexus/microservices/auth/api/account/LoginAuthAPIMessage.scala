package riichinexus.microservices.auth.api.account
import riichinexus.microservices.player.api.`private`.ResolvePlayerPrivateAPIMessage

import java.time.Instant

import cats.effect.IO
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.microservices.player.objects.`private`.PlayerPrivateView
import riichinexus.microservices.auth.domain.account.model.AuthenticationFailure
import riichinexus.microservices.auth.domain.account.functions.AccountCredentialFunctions
import riichinexus.microservices.auth.domain.session.functions.AuthenticatedSessionFunctions
import riichinexus.microservices.auth.domain.account.functions.PasswordHashFunctions
import riichinexus.microservices.auth.domain.account.model.AccountCredential
import riichinexus.microservices.auth.domain.session.model.AuthenticatedSession
import riichinexus.microservices.auth.objects.authorization.Role
import riichinexus.microservices.auth.objects.session.AuthSuccessView
import riichinexus.microservices.auth.objects.session.CurrentSessionRoleFlags
import riichinexus.microservices.auth.tables.accountcredential.AccountCredentialTable
import riichinexus.microservices.auth.tables.authenticatedsession.AuthenticatedSessionTable
/** 使用账号密码登录。 */
final case class LoginAuthAPIMessage(
    username: String,
    password: String
) extends APIMessage[AuthSuccessView]:

  private val SessionTtl = java.time.Duration.ofDays(30)

  override def plan(context: ApiPlanContext): IO[AuthSuccessView] =
    for
      loginAt <- IO.realTimeInstant
      normalizedUsername <- IO.blocking(AccountCredentialFunctions.normalizeUsername(username))
      credential <- authenticateCredential(context, normalizedUsername, password)
      player <- resolveCredentialPlayer(context, credential)
      _ <- IO.blocking(ensureActivePlayer(player))
      session <- createSession(context, loginAt, credential)
    yield authSuccessView(normalizedUsername, player, session)

  private def authenticateCredential(
      context: ApiPlanContext,
      username: String,
      password: String
  ): IO[AccountCredential] =
    IO.blocking {
      require(password.nonEmpty, "Password is required")
      val credential = AccountCredentialTable.findByUsername(context.connection, username)
        .getOrElse(throw AuthenticationFailure("Invalid username or password", "invalid_credentials"))
      if !PasswordHashFunctions.verify(password, credential) then
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
      loginAt: Instant,
      credential: AccountCredential
  ): IO[AuthenticatedSession] =
    IO.blocking(
      AuthenticatedSessionTable.save(
        context.connection,
        AuthenticatedSessionFunctions.create(
          username = credential.username,
          playerId = credential.playerId,
          createdAt = loginAt,
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
