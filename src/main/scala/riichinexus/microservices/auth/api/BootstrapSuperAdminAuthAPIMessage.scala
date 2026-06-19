package riichinexus.microservices.auth.api

import cats.effect.IO
import riichinexus.microservices.auth.domain.AuthenticationFailure
import riichinexus.system.api.AuthorizationFailure
import riichinexus.microservices.auth.domain.functions.{AccountCredentialFunctions, AuthenticatedSessionFunctions, PasswordHashFunctions}
import riichinexus.microservices.auth.domain.model.{AccountCredential, AuthenticatedSession}
import riichinexus.microservices.auth.objects.Role
import riichinexus.microservices.auth.objects.apiTypes.{AuthSuccessView, BootstrapSuperAdminRequest, CurrentSessionRoleFlags}
import riichinexus.microservices.auth.security.PasswordSaltGenerator
import riichinexus.microservices.auth.tables.accountcredential.AccountCredentialTable
import riichinexus.microservices.auth.tables.authenticatedsession.AuthenticatedSessionTable
import riichinexus.microservices.player.api.`private`.{ListAllPlayersPrivateAPIMessage, RecordPlayerNicknameUpdatePrivateAPIMessage, RecordPlayerSuperAdminGrantPrivateAPIMessage, ResolvePlayerByUserIdPrivateAPIMessage, ResolvePlayerPrivateAPIMessage}
import riichinexus.microservices.player.objects.`private`.PlayerPrivateView
import riichinexus.microservices.player.objects.{RankPlatform, RankSnapshot}
import riichinexus.microservices.player.api.CreatePlayerAPIMessage
import riichinexus.microservices.player.objects.apiTypes.CreatePlayerRequest
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import upickle.default.ReadWriter

import java.time.Instant
import java.util.NoSuchElementException

/** 初始化系统超级管理员账号。 */
final case class BootstrapSuperAdminAuthAPIMessage(
    request: BootstrapSuperAdminRequest
) extends APIMessage[AuthSuccessView] derives ReadWriter:

  private val DefaultRank = RankSnapshot(RankPlatform.Custom, "Unranked")
  private val SessionTtl = java.time.Duration.ofDays(30)

  override def plan(context: ApiPlanContext): IO[AuthSuccessView] =
    for
      initializedAt <- IO.realTimeInstant
      command <- IO.blocking(buildCommand(initializedAt))
      configuredKey <- loadConfiguredBootstrapKey
      _ <- validateBootstrapRequest(command, configuredKey)
      _ <- ensureUsernameAvailable(context, command.username)
      _ <- ensureNoSuperAdmin(context)
      player <- resolveRegisteredPlayer(context, command)
      _ <- IO.blocking(ensureActivePlayer(player))
      _ <- saveCredential(context, command, player)
      superAdminPlayer <- grantSuperAdmin(context, command, player)
      session <- createSession(context, command, superAdminPlayer)
    yield authSuccessView(command.username, superAdminPlayer, session)

  private def buildCommand(initializedAt: Instant): BootstrapSuperAdminAuthCommand =
    BootstrapSuperAdminAuthCommand(
      bootstrapKey = Option(request.bootstrapKey).map(_.trim).getOrElse(""),
      username = AccountCredentialFunctions.normalizeUsername(request.username),
      password = request.password,
      displayName = normalizeDisplayName(request.displayName),
      initializedAt = initializedAt
    )

  private def loadConfiguredBootstrapKey: IO[String] =
    IO.blocking(configuredBootstrapKey)

  private def validateBootstrapRequest(
      command: BootstrapSuperAdminAuthCommand,
      configuredKey: String
  ): IO[Unit] =
    IO.blocking {
      validateBootstrapKey(command.bootstrapKey, configuredKey)
      validatePassword(command.password)
    }

  private def ensureUsernameAvailable(
      context: ApiPlanContext,
      username: String
  ): IO[Unit] =
    IO.blocking {
      if AccountCredentialTable.findByUsername(context.connection, username).nonEmpty then
        throw IllegalArgumentException("Username " + username + " is already registered")
    }

  private def resolveRegisteredPlayer(
      context: ApiPlanContext,
      command: BootstrapSuperAdminAuthCommand
  ): IO[PlayerPrivateView] =
    ListAllPlayersPrivateAPIMessage()
      .plan(context)
      .flatMap { players =>
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
      command: BootstrapSuperAdminAuthCommand
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
      command: BootstrapSuperAdminAuthCommand,
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
          createdAt = command.initializedAt,
          updatedAt = command.initializedAt
        )
      )
    }

  private def grantSuperAdmin(
      context: ApiPlanContext,
      command: BootstrapSuperAdminAuthCommand,
      player: PlayerPrivateView
  ): IO[PlayerPrivateView] =
    RecordPlayerSuperAdminGrantPrivateAPIMessage(player.id, command.initializedAt, None)
      .plan(context)
      .flatMap(_ =>
        ResolvePlayerPrivateAPIMessage(player.id).plan(context).map(
          _.getOrElse(throw NoSuchElementException(s"Player ${player.id.value} was not found"))
        )
      )

  private def createSession(
      context: ApiPlanContext,
      command: BootstrapSuperAdminAuthCommand,
      player: PlayerPrivateView
  ): IO[AuthenticatedSession] =
    IO.blocking(
      AuthenticatedSessionTable.save(
        context.connection,
        AuthenticatedSessionFunctions.create(
          username = command.username,
          playerId = player.id,
          createdAt = command.initializedAt,
          ttl = SessionTtl
        )
      )
    )

  private def configuredBootstrapKey: String =
    sys.env
      .get("RIICHI_SUPERADMIN_BOOTSTRAP_KEY")
      .orElse(sys.env.get("SUPERADMIN_BOOTSTRAP_KEY"))
      .map(_.trim)
      .filter(_.nonEmpty)
      .getOrElse(throw AuthorizationFailure("Super admin bootstrap key is not configured"))

  private def validateBootstrapKey(value: String, configuredKey: String): Unit =
    if value != configuredKey then
      throw AuthenticationFailure("Invalid super admin bootstrap key", "invalid_bootstrap_key")

  private def ensureNoSuperAdmin(context: ApiPlanContext): IO[Unit] =
    ListAllPlayersPrivateAPIMessage().plan(context).map { players =>
      if players.exists(_.roleGrants.exists(_.role == Role.SuperAdmin)) then
        throw AuthorizationFailure("Super admin has already been initialized")
    }

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
        "Player " + player.id.value + " is not active",
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

  private final case class BootstrapSuperAdminAuthCommand(
      bootstrapKey: String,
      username: String,
      password: String,
      displayName: String,
      initializedAt: Instant
  )
