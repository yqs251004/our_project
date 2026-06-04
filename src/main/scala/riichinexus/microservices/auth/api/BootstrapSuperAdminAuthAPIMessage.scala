package riichinexus.microservices.auth.api

import cats.effect.IO
import riichinexus.microservices.auth.domain.{AuthenticationFailure, AuthorizationFailure}
import riichinexus.microservices.auth.domain.functions.{
  AccountCredentialFunctions,
  AuthenticatedSessionFunctions,
  PasswordHashFunctions,
  RoleGrantFunctions
}
import riichinexus.microservices.auth.domain.model.{AccountCredential, AuthenticatedSession}
import riichinexus.microservices.auth.objects.Role
import riichinexus.microservices.auth.objects.apiTypes.{AuthSuccessView, CurrentSessionRoleFlags}
import riichinexus.microservices.auth.security.PasswordSaltGenerator
import riichinexus.microservices.auth.tables.accountcredential.AccountCredentialTable
import riichinexus.microservices.auth.tables.authenticatedsession.AuthenticatedSessionTable
import riichinexus.microservices.player.domain.Player
import riichinexus.microservices.player.domain.functions.{PlayerPersistenceFunctions, PlayerRoleFunctions}
import riichinexus.microservices.player.objects.{PlayerStatus, RankPlatform, RankSnapshot}
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import upickle.default.*

import java.sql.Connection
import java.time.Instant

final case class BootstrapSuperAdminAuthAPIMessage(
    bootstrapKey: String,
    username: String,
    password: String,
    displayName: String
) extends APIMessage[AuthSuccessView] derives ReadWriter:

  private val DefaultRank = RankSnapshot(RankPlatform.Custom, "Unranked")
  private val SessionTtl = java.time.Duration.ofDays(30)

  override def plan(context: ApiPlanContext): IO[AuthSuccessView] =
    for
      initializedAt <- IO.realTimeInstant
      command = BootstrapSuperAdminAuthCommand(
        bootstrapKey = Option(bootstrapKey).map(_.trim).getOrElse(""),
        username = AccountCredentialFunctions.normalizeUsername(username),
        password = password,
        displayName = normalizeDisplayName(displayName),
        initializedAt = initializedAt
      )
      result <- IO.blocking(initializeSuperAdmin(context, command))
    yield AuthSuccessView(
      userId = result.player.id.value,
      username = result.username,
      displayName = result.player.nickname,
      token = result.session.token,
      roles = registeredRoleFlags(result.player)
    )

  private def initializeSuperAdmin(
      context: ApiPlanContext,
      command: BootstrapSuperAdminAuthCommand
  ): BootstrapSuperAdminAuthResult =
    val connection = context.connection
    validateBootstrapKey(command.bootstrapKey)
    ensureNoSuperAdmin(connection)
    validatePassword(command.password)
    if AccountCredentialTable.findByUsername(connection, command.username).nonEmpty then
      throw IllegalArgumentException("Username " + command.username + " is already registered")

    val player = resolveRegisteredPlayer(context, command)
    ensureActivePlayer(player)
    saveCredential(connection, command, player)
    val superAdminPlayer = PlayerPersistenceFunctions.savePlayer(
      connection,
      PlayerRoleFunctions.grantRole(
        player,
        RoleGrantFunctions.superAdmin(command.initializedAt, None)
      )
    )
    val session = AuthenticatedSessionTable.save(
      connection,
      AuthenticatedSessionFunctions.create(
        username = command.username,
        playerId = superAdminPlayer.id,
        createdAt = command.initializedAt,
        ttl = SessionTtl
      )
    )
    BootstrapSuperAdminAuthResult(command.username, superAdminPlayer, session)

  private def resolveRegisteredPlayer(
      context: ApiPlanContext,
      command: BootstrapSuperAdminAuthCommand
  ): Player =
    PlayerPersistenceFunctions.findAllPlayers(context.connection).find(_.userId.equalsIgnoreCase(command.username)) match
      case Some(existing) if existing.nickname == command.displayName =>
        existing
      case Some(existing) =>
        PlayerPersistenceFunctions.savePlayer(context.connection, existing.copy(nickname = command.displayName))
      case None =>
        PlayerPersistenceFunctions.createPlayer(
          connection = context.connection,
          userId = command.username,
          nickname = command.displayName,
          rank = DefaultRank,
          registeredAt = command.initializedAt,
          initialElo = 1500
        )

  private def saveCredential(
      connection: Connection,
      command: BootstrapSuperAdminAuthCommand,
      player: Player
  ): AccountCredential =
    val passwordDigest = PasswordHashFunctions.digest(command.password, PasswordSaltGenerator.nextSalt())
    AccountCredentialTable.save(
      connection,
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

  private def configuredBootstrapKey: String =
    sys.env
      .get("RIICHI_SUPERADMIN_BOOTSTRAP_KEY")
      .orElse(sys.env.get("SUPERADMIN_BOOTSTRAP_KEY"))
      .map(_.trim)
      .filter(_.nonEmpty)
      .getOrElse(throw AuthorizationFailure("Super admin bootstrap key is not configured"))

  private def validateBootstrapKey(value: String): Unit =
    if value != configuredBootstrapKey then
      throw AuthenticationFailure("Invalid super admin bootstrap key", "invalid_bootstrap_key")

  private def ensureNoSuperAdmin(connection: Connection): Unit =
    if PlayerPersistenceFunctions.findAllPlayers(connection).exists(_.roleGrants.exists(_.role == Role.SuperAdmin)) then
      throw AuthorizationFailure("Super admin has already been initialized")

  private def normalizeDisplayName(displayName: String): String =
    Option(displayName)
      .map(_.trim)
      .filter(_.nonEmpty)
      .getOrElse(throw IllegalArgumentException("Display name is required"))

  private def validatePassword(password: String): Unit =
    require(password.length >= 8, "Password must be at least 8 characters")

  private def ensureActivePlayer(player: Player): Unit =
    if player.status != PlayerStatus.Active then
      throw AuthenticationFailure(
        "Player " + player.id.value + " is not active",
        "inactive_account"
      )

  private def registeredRoleFlags(player: Player): CurrentSessionRoleFlags =
    CurrentSessionRoleFlags(
      isGuest = false,
      isRegisteredPlayer = true,
      isClubAdmin = player.roleGrants.exists(_.role == Role.ClubAdmin),
      isTournamentAdmin = player.roleGrants.exists(_.role == Role.TournamentAdmin),
      isSuperAdmin = player.roleGrants.exists(_.role == Role.SuperAdmin)
    )

  private final case class BootstrapSuperAdminAuthCommand(
      bootstrapKey: String,
      username: String,
      password: String,
      displayName: String,
      initializedAt: Instant
  )

  private final case class BootstrapSuperAdminAuthResult(
      username: String,
      player: Player,
      session: AuthenticatedSession
  )
