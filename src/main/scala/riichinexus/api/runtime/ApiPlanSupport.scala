package riichinexus.api.runtime

import java.time.Instant
import java.util.NoSuchElementException

import scala.util.Try

import riichinexus.bootstrap.*
import riichinexus.domain.model.*
import riichinexus.domain.service.AuthorizationService
import riichinexus.microservices.auth.objects.apiTypes.*

final class ApiPlanSupport(
    val executionContext: ApiExecutionContext
):
  val authModule: AuthModuleContext = executionContext.authModule
  val playerModule: PlayerModuleContext = executionContext.playerModule
  val clubModule: ClubModuleContext = executionContext.clubModule
  val dictionaryModule: DictionaryModuleContext = executionContext.dictionaryModule
  val publicQueryModule: PublicQueryModuleContext = executionContext.publicQueryModule
  val opsAnalyticsModule: OpsAnalyticsModuleContext = executionContext.opsAnalyticsModule
  val tournamentModule: TournamentModuleContext = executionContext.tournamentModule
  val platformAdminModule: PlatformAdminModuleContext = executionContext.platformAdminModule
  val tournamentAppealModule: TournamentAppealModuleContext = executionContext.tournamentAppealModule
  val authorizationService: AuthorizationService = executionContext.authorizationService
  val storageLabel: String = executionContext.storageLabel

  def principal(playerId: PlayerId): AccessPrincipal =
    authModule.playerTable
      .find(playerId)
      .map(_.asPrincipal)
      .getOrElse(throw NoSuchElementException(s"Player ${playerId.value} was not found"))

  def guestPrincipal(sessionId: GuestSessionId): AccessPrincipal =
    touchActiveGuestSession(sessionId)
      .map(AccessPrincipal.guest)
      .getOrElse(throw NoSuchElementException(s"Guest session ${sessionId.value} was not found"))

  def requestActor(guestSessionId: Option[GuestSessionId], operatorId: Option[PlayerId]): AccessPrincipal =
    if guestSessionId.nonEmpty && operatorId.nonEmpty then
      throw IllegalArgumentException("guestSessionId and operatorId cannot be provided together")

    guestSessionId.map(guestPrincipal)
      .orElse(operatorId.map(principal))
      .getOrElse(AccessPrincipal.guest())

  def requirePermission(
      principal: AccessPrincipal,
      permission: Permission,
      clubId: Option[ClubId] = None,
      tournamentId: Option[TournamentId] = None,
      subjectPlayerId: Option[PlayerId] = None
  ): Unit =
    authorizationService.requirePermission(
      principal = principal,
      permission = permission,
      clubId = clubId,
      tournamentId = tournamentId,
      subjectPlayerId = subjectPlayerId
    )

  def resolveCurrentSessionView(
      operatorId: Option[PlayerId],
      guestSessionId: Option[GuestSessionId]
  ): CurrentSessionView =
    if operatorId.nonEmpty && guestSessionId.nonEmpty then
      throw IllegalArgumentException("guestSessionId and operatorId cannot be provided together")

    operatorId.map(playerId =>
      authModule.playerTable.find(playerId)
        .getOrElse(throw NoSuchElementException(s"Player ${playerId.value} was not found"))
    ) match
      case Some(player) =>
        CurrentSessionView(
          principalKind = SessionPrincipalKind.RegisteredPlayer,
          principalId = player.id.value,
          displayName = player.nickname,
          authenticated = true,
          roles = registeredRoleFlags(player),
          player = Some(player)
        )
      case None =>
        guestSessionId.map(sessionId =>
          touchActiveGuestSession(sessionId)
            .getOrElse(throw NoSuchElementException(s"Guest session ${sessionId.value} was not found"))
        ) match
          case Some(session) =>
            CurrentSessionView(
              principalKind = SessionPrincipalKind.Guest,
              principalId = session.id.value,
              displayName = session.displayName,
              authenticated = true,
              roles = CurrentSessionRoleFlags(
                isGuest = true,
                isRegisteredPlayer = false,
                isClubAdmin = false,
                isTournamentAdmin = false,
                isSuperAdmin = false
              ),
              guestSession = Some(session)
            )
          case None =>
            CurrentSessionView(
              principalKind = SessionPrincipalKind.Anonymous,
              principalId = "anonymous",
              displayName = "Guest",
              authenticated = false,
              roles = CurrentSessionRoleFlags(
                isGuest = true,
                isRegisteredPlayer = false,
                isClubAdmin = false,
                isTournamentAdmin = false,
                isSuperAdmin = false
              )
            )

  def registeredRoleFlags(player: Player): CurrentSessionRoleFlags =
    CurrentSessionRoleFlags(
      isGuest = false,
      isRegisteredPlayer = true,
      isClubAdmin = player.roleGrants.exists(_.role == RoleKind.ClubAdmin),
      isTournamentAdmin = player.roleGrants.exists(_.role == RoleKind.TournamentAdmin),
      isSuperAdmin = player.roleGrants.exists(_.role == RoleKind.SuperAdmin)
    )

  def parseEnum[E](label: String, value: String)(parse: String => E): E =
    Try(parse(value)).getOrElse(throw IllegalArgumentException(s"Invalid $label: $value"))

  def containsIgnoreCase(value: String, fragment: String): Boolean =
    value.toLowerCase.contains(fragment.toLowerCase)

  private def touchActiveGuestSession(
      sessionId: GuestSessionId,
      seenAt: Instant = Instant.now()
  ): Option[GuestAccessSession] =
    authModule.transactionManager.inTransaction {
      authModule.guestSessionRepository.findById(sessionId).map { session =>
        require(session.canAuthenticate(seenAt), inactiveSessionMessage(session, seenAt))
        authModule.guestSessionRepository.save(session.touch(seenAt))
      }
    }

  private def inactiveSessionMessage(session: GuestAccessSession, at: Instant): String =
    if session.isRevoked then
      s"Guest session ${session.id.value} has been revoked"
    else if session.isUpgraded then
      s"Guest session ${session.id.value} has already been upgraded to player access"
    else if session.isExpired(at) then
      s"Guest session ${session.id.value} expired at ${session.expiresAt}"
    else
      s"Guest session ${session.id.value} cannot be used for authentication"

object ApiPlanSupport:

  def apply(executionContext: ApiExecutionContext): ApiPlanSupport =
    new ApiPlanSupport(executionContext)
