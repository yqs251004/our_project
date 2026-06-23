package riichinexus.microservices.auth.api.account
import riichinexus.microservices.auth.domain.account.model.AuthenticationFailure
import riichinexus.microservices.player.api.`private`.ListAllPlayersPrivateAPIMessage
import riichinexus.microservices.player.api.`private`.RecordPlayerNicknameUpdatePrivateAPIMessage
import riichinexus.microservices.player.api.`private`.ResolvePlayerByUserIdPrivateAPIMessage
import riichinexus.microservices.player.api.`private`.ResolvePlayerPrivateAPIMessage

import java.time.Instant
import java.util.NoSuchElementException

import cats.effect.IO
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.microservices.player.objects.`private`.PlayerPrivateView
import riichinexus.microservices.player.objects.{RankPlatform, RankSnapshot}
import riichinexus.microservices.auth.domain.account.functions.AccountCredentialFunctions
import riichinexus.microservices.auth.domain.session.functions.AuthenticatedSessionFunctions
import riichinexus.microservices.auth.domain.account.functions.PasswordHashFunctions
import riichinexus.microservices.auth.domain.account.model.AccountCredential
import riichinexus.microservices.auth.domain.session.model.AuthenticatedSession
import riichinexus.microservices.auth.objects.authorization.Role
import riichinexus.microservices.auth.objects.session.AuthSuccessView
import riichinexus.microservices.auth.objects.session.CurrentSessionRoleFlags
import riichinexus.microservices.auth.security.PasswordSaltGenerator
import riichinexus.microservices.auth.tables.accountcredential.AccountCredentialTable
import riichinexus.microservices.auth.tables.authenticatedsession.AuthenticatedSessionTable
import riichinexus.microservices.player.api.CreatePlayerAPIMessage
import riichinexus.microservices.player.objects.apiTypes.CreatePlayerRequest
/** 注册账号并创建或绑定玩家档案。 */
final case class RegisterAuthAPIMessage(
    username: String,
    password: String,
    displayName: String
) extends APIMessage[AuthSuccessView]:

  private val DefaultRank = RankSnapshot(RankPlatform.Custom, "Unranked")
  private val SessionTtl = java.time.Duration.ofDays(30)

  override def plan(context: ApiPlanContext): IO[AuthSuccessView] =
    for
      registeredAt <- IO.realTimeInstant
      normalizedUsername <- IO.blocking(AccountCredentialFunctions.normalizeUsername(username))
      normalizedDisplayName <- IO.blocking(normalizeDisplayName(displayName))
      _ <- validateRegistrationRequest(password)
      _ <- ensureUsernameAvailable(context, normalizedUsername)
      player <- resolveRegisteredPlayer(context, normalizedUsername, normalizedDisplayName)
      _ <- IO.blocking(ensureActivePlayer(player))
      _ <- saveCredential(context, normalizedUsername, password, registeredAt, player)
      session <- createSession(context, normalizedUsername, registeredAt, player)
    yield authSuccessView(normalizedUsername, player, session)

  private def validateRegistrationRequest(password: String): IO[Unit] =
    IO.blocking(validatePassword(password))

  private def ensureUsernameAvailable(context: ApiPlanContext, username: String): IO[Unit] =
    IO.blocking {
      if AccountCredentialTable.findByUsername(context.connection, username).nonEmpty then
        throw IllegalArgumentException(s"Username ${username} is already registered")
    }

  private def resolveRegisteredPlayer(
      context: ApiPlanContext,
      username: String,
      displayName: String
  ): IO[PlayerPrivateView] =
    ListAllPlayersPrivateAPIMessage().plan(context).flatMap { players =>
      players.find(_.userId.equalsIgnoreCase(username)) match
        case Some(existing) if existing.nickname == displayName =>
          IO.pure(existing)
        case Some(existing) =>
          RecordPlayerNicknameUpdatePrivateAPIMessage(existing.id, displayName)
            .plan(context)
            .flatMap(_ =>
              ResolvePlayerPrivateAPIMessage(existing.id).plan(context).map(
                _.getOrElse(throw NoSuchElementException(s"Player ${existing.id.value} was not found"))
              )
            )
        case None =>
          createPlayerViaPublicAPI(context, username, displayName)
    }

  private def createPlayerViaPublicAPI(
      context: ApiPlanContext,
      username: String,
      displayName: String
  ): IO[PlayerPrivateView] =
    for
      _ <- CreatePlayerAPIMessage(
        CreatePlayerRequest(
          userId = username,
          nickname = displayName,
          rankPlatform = RankPlatform.toString(DefaultRank.platform),
          tier = DefaultRank.tier,
          stars = DefaultRank.stars,
          initialElo = 1500
        )
      ).plan(context)
      player <- ResolvePlayerByUserIdPrivateAPIMessage(username).plan(context).map(
        _.getOrElse(throw IllegalStateException(s"Player ${username} was not created"))
      )
    yield player

  private def saveCredential(
      context: ApiPlanContext,
      username: String,
      password: String,
      registeredAt: Instant,
      player: PlayerPrivateView
  ): IO[AccountCredential] =
    IO.blocking {
      val passwordDigest = PasswordHashFunctions.digest(password, PasswordSaltGenerator.nextSalt())
      AccountCredentialTable.save(
        context.connection,
        AccountCredential(
          username = username,
          playerId = player.id,
          passwordHash = passwordDigest.hash,
          passwordSalt = passwordDigest.salt,
          passwordIterations = passwordDigest.iterations,
          createdAt = registeredAt,
          updatedAt = registeredAt
        )
      )
    }

  private def createSession(
      context: ApiPlanContext,
      username: String,
      registeredAt: Instant,
      player: PlayerPrivateView
  ): IO[AuthenticatedSession] =
    IO.blocking(
      AuthenticatedSessionTable.save(
        context.connection,
        AuthenticatedSessionFunctions.create(
          username = username,
          playerId = player.id,
          createdAt = registeredAt,
          ttl = SessionTtl
        )
      )
    )

  private def normalizeDisplayName(displayName: String): String =
    Option(displayName)
      .map(_.trim)
      .filter(_.nonEmpty)
      .getOrElse(throw IllegalArgumentException("Display name is required"))

  private def validatePassword(password: String): Unit =
    require(password.length >= 8, "Password must be at least 8 characters")

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
