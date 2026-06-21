package riichinexus.microservices.auth.api
import riichinexus.microservices.auth.domain.AuthenticationFailure
import riichinexus.microservices.player.api.`private`.{ListAllPlayersPrivateAPIMessage, RecordPlayerNicknameUpdatePrivateAPIMessage, ResolvePlayerByUserIdPrivateAPIMessage, ResolvePlayerPrivateAPIMessage}

import java.time.Instant
import java.util.NoSuchElementException

import cats.effect.IO
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.microservices.player.objects.`private`.PlayerPrivateView
import riichinexus.microservices.player.objects.{RankPlatform, RankSnapshot}
import riichinexus.microservices.auth.domain.functions.{AccountCredentialFunctions, AuthenticatedSessionFunctions, PasswordHashFunctions}
import riichinexus.microservices.auth.domain.model.{AccountCredential, AuthenticatedSession}
import riichinexus.microservices.auth.objects.Role
import riichinexus.microservices.auth.objects.apiTypes.{AuthSuccessView, CurrentSessionRoleFlags}
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
      command <- IO.blocking(buildCommand(registeredAt))
      _ <- validateRegistrationRequest(command)
      _ <- ensureUsernameAvailable(context, command.username)
      player <- resolveRegisteredPlayer(context, command)
      _ <- IO.blocking(ensureActivePlayer(player))
      _ <- saveCredential(context, command, player)
      session <- createSession(context, command, player)
    yield authSuccessView(command.username, player, session)

  private def buildCommand(registeredAt: Instant): RegisterAuthCommand =
    RegisterAuthCommand(
      username = AccountCredentialFunctions.normalizeUsername(username),
      password = password,
      displayName = normalizeDisplayName(displayName),
      registeredAt = registeredAt
    )

  private def validateRegistrationRequest(command: RegisterAuthCommand): IO[Unit] =
    IO.blocking(validatePassword(command.password))

  private def ensureUsernameAvailable(context: ApiPlanContext, username: String): IO[Unit] =
    IO.blocking {
      if AccountCredentialTable.findByUsername(context.connection, username).nonEmpty then
        throw IllegalArgumentException(s"Username ${username} is already registered")
    }

  private def resolveRegisteredPlayer(
      context: ApiPlanContext,
      command: RegisterAuthCommand
  ): IO[PlayerPrivateView] =
    ListAllPlayersPrivateAPIMessage().plan(context).flatMap { players =>
      players.find(_.userId.equalsIgnoreCase(command.username)) match
        case Some(existing) if existing.nickname == command.displayName =>
          IO.pure(existing)
        case Some(existing) =>
          RecordPlayerNicknameUpdatePrivateAPIMessage(existing.id, command.displayName)
            .plan(context)
            .flatMap(_ =>
              ResolvePlayerPrivateAPIMessage(existing.id).plan(context).map(
                _.getOrElse(throw NoSuchElementException(s"Player ${existing.id.value} was not found"))
              )
            )
        case None =>
          createPlayerViaPublicAPI(context, command)
    }

  private def createPlayerViaPublicAPI(
      context: ApiPlanContext,
      command: RegisterAuthCommand
  ): IO[PlayerPrivateView] =
    for
      _ <- CreatePlayerAPIMessage(
        CreatePlayerRequest(
          userId = command.username,
          nickname = command.displayName,
          rankPlatform = RankPlatform.toString(DefaultRank.platform),
          tier = DefaultRank.tier,
          stars = DefaultRank.stars,
          initialElo = 1500
        )
      ).plan(context)
      player <- ResolvePlayerByUserIdPrivateAPIMessage(command.username).plan(context).map(
        _.getOrElse(throw IllegalStateException(s"Player ${command.username} was not created"))
      )
    yield player

  private def saveCredential(
      context: ApiPlanContext,
      command: RegisterAuthCommand,
      player: PlayerPrivateView
  ): IO[AccountCredential] =
    IO.blocking {
      val passwordDigest = PasswordHashFunctions.digest(command.password, PasswordSaltGenerator.nextSalt())
      AccountCredentialTable.save(
        context.connection,
        AccountCredential(
          username = command.username,
          playerId = player.id,
          passwordHash = passwordDigest.hash,
          passwordSalt = passwordDigest.salt,
          passwordIterations = passwordDigest.iterations,
          createdAt = command.registeredAt,
          updatedAt = command.registeredAt
        )
      )
    }

  private def createSession(
      context: ApiPlanContext,
      command: RegisterAuthCommand,
      player: PlayerPrivateView
  ): IO[AuthenticatedSession] =
    IO.blocking(
      AuthenticatedSessionTable.save(
        context.connection,
        AuthenticatedSessionFunctions.create(
          username = command.username,
          playerId = player.id,
          createdAt = command.registeredAt,
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

  /** 注册账号并创建玩家身份时使用的内部命令。 */
  private final case class RegisterAuthCommand(
      username: String,
      password: String,
      displayName: String,
      registeredAt: Instant
  )
