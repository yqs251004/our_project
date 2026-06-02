package riichinexus.microservices.auth.api
import riichinexus.microservices.player.domain.functions.PlayerPersistenceFunctions

import java.time.Instant

import cats.effect.IO
import cats.effect.unsafe.implicits.global
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
import riichinexus.microservices.auth.domain.AuthenticationFailure
import riichinexus.microservices.auth.domain.functions.{AccountCredentialFunctions, AuthenticatedSessionFunctions, PasswordHashFunctions}
import riichinexus.microservices.auth.domain.model.{AccountCredential, AuthenticatedSession}
import riichinexus.microservices.auth.objects.Role
import riichinexus.microservices.auth.objects.apiTypes.{AuthSuccessView, CurrentSessionRoleFlags}
import riichinexus.microservices.auth.tables.accountcredential.AccountCredentialTable
import riichinexus.microservices.auth.tables.authenticatedsession.AuthenticatedSessionTable
import riichinexus.microservices.player.api.{CreatePlayerAPIMessage, GetPlayerAPIMessage, ListPlayersAPIMessage}
import upickle.default.*

final case class LoginAuthAPIMessage(
    username: String,
    password: String
) extends APIMessage[AuthSuccessView] derives ReadWriter:

  private val SessionTtl = java.time.Duration.ofDays(30)

  override def plan(context: ApiPlanContext): IO[AuthSuccessView] =
    for
      loginAt <- IO.realTimeInstant
      command = LoginCommand(AccountCredentialFunctions.normalizeUsername(username), password, loginAt)
      result <- IO.blocking {
        {
          login(context, command)
        }
      }
    yield AuthSuccessView(
      userId = result.player.id.value,
      username = result.credential.username,
      displayName = result.player.nickname,
      token = result.session.token,
      roles = registeredRoleFlags(result.player)
    )

  private def login(context: ApiPlanContext, command: LoginCommand): LoginResult =
    val connection = context.connection
    require(command.password.nonEmpty, "Password is required")
    val credential = AccountCredentialTable.findByUsername(connection, command.username)
      .getOrElse(throw AuthenticationFailure("Invalid username or password", "invalid_credentials"))
    if !PasswordHashFunctions.verify(command.password, credential) then
      throw AuthenticationFailure("Invalid username or password", "invalid_credentials")

    val player = PlayerPersistenceFunctions.findPlayer(context.connection, credential.playerId)
      .getOrElse(throw AuthenticationFailure(s"Player ${credential.playerId.value} was not found", "invalid_credentials"))
    ensureActivePlayer(player)

    val session = AuthenticatedSessionTable.save(
      connection,
      AuthenticatedSessionFunctions.create(
        username = credential.username,
        playerId = credential.playerId,
        createdAt = command.loginAt,
        ttl = SessionTtl
      )
    )
    LoginResult(credential, player, session)

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

  private final case class LoginCommand(
      username: String,
      password: String,
      loginAt: Instant
  )

  private final case class LoginResult(
      credential: AccountCredential,
      player: Player,
      session: AuthenticatedSession
  )
