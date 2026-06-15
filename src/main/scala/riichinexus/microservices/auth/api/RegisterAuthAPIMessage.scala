package riichinexus.microservices.auth.api
import riichinexus.microservices.auth.domain.AuthenticationFailure
import riichinexus.microservices.player.api.`private`.*

import java.time.Instant

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
import riichinexus.microservices.player.domain.Player
import riichinexus.microservices.player.objects.*
import riichinexus.microservices.auth.domain.functions.{AccountCredentialFunctions, AuthenticatedSessionFunctions, PasswordHashFunctions}
import riichinexus.microservices.auth.domain.model.{AccountCredential, AuthenticatedSession}
import riichinexus.microservices.auth.objects.Role
import riichinexus.microservices.auth.objects.apiTypes.{AuthSuccessView, CurrentSessionRoleFlags}
import riichinexus.microservices.auth.security.PasswordSaltGenerator
import riichinexus.microservices.auth.tables.accountcredential.AccountCredentialTable
import riichinexus.microservices.auth.tables.authenticatedsession.AuthenticatedSessionTable
import riichinexus.microservices.player.api.{CreatePlayerAPIMessage, GetPlayerAPIMessage, ListPlayersAPIMessage}
import upickle.default.*

final case class RegisterAuthAPIMessage(
    username: String,
    password: String,
    displayName: String
) extends APIMessage[AuthSuccessView] derives ReadWriter:

  private val DefaultRank = RankSnapshot(RankPlatform.Custom, "Unranked")
  private val SessionTtl = java.time.Duration.ofDays(30)

  override def plan(context: ApiPlanContext): IO[AuthSuccessView] =
    for
      registeredAt <- IO.realTimeInstant
      command = RegisterAuthCommand(
        username = AccountCredentialFunctions.normalizeUsername(username),
        password = password,
        displayName = normalizeDisplayName(displayName),
        registeredAt = registeredAt
      )
      result <- register(context, command)
    yield AuthSuccessView(
      userId = result.player.id.value,
      username = result.username,
      displayName = result.player.nickname,
      token = result.session.token,
      roles = registeredRoleFlags(result.player)
    )

  private def register(context: ApiPlanContext, command: RegisterAuthCommand): IO[RegisterAuthResult] =
    val connection = context.connection
    for
      _ <- IO.blocking {
        validatePassword(command.password)
        if AccountCredentialTable.findByUsername(connection, command.username).nonEmpty then
          throw IllegalArgumentException(s"Username ${command.username} is already registered")
      }
      player <- resolveRegisteredPlayer(context, command)
      _ <- IO.blocking {
        ensureActivePlayer(player)
        saveCredential(connection, command, player)
      }
      session <- IO.blocking(
        AuthenticatedSessionTable.save(
          connection,
          AuthenticatedSessionFunctions.create(
            username = command.username,
            playerId = player.id,
            createdAt = command.registeredAt,
            ttl = SessionTtl
          )
        )
      )
    yield RegisterAuthResult(command.username, player, session)

  private def resolveRegisteredPlayer(
      context: ApiPlanContext,
      command: RegisterAuthCommand
  ): IO[Player] =
    ListAllPlayersPrivateAPIMessage().plan(context).flatMap { players =>
      players.find(_.userId.equalsIgnoreCase(command.username)) match
        case Some(existing) if existing.nickname == command.displayName =>
          IO.pure(existing)
        case Some(existing) =>
          SavePlayerPrivateAPIMessage(existing.copy(nickname = command.displayName)).plan(context)
        case None =>
          CreatePlayerPrivateAPIMessage(command.username, command.displayName, DefaultRank, command.registeredAt, 1500).plan(context)
    }

  private def saveCredential(
      connection: java.sql.Connection,
      command: RegisterAuthCommand,
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
        createdAt = command.registeredAt,
        updatedAt = command.registeredAt
      )
    )

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
        s"Player ${player.id.value} is not active",
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

  private final case class RegisterAuthCommand(
      username: String,
      password: String,
      displayName: String,
      registeredAt: Instant
  )

  private final case class RegisterAuthResult(
      username: String,
      player: Player,
      session: AuthenticatedSession
  )
