package riichinexus.microservices.club.api

import java.time.Instant
import java.util.NoSuchElementException

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.bootstrap.ClubModuleContext
import riichinexus.domain.model.*
import riichinexus.domain.service.*
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.club.domain.ClubProjectionRefresher
import riichinexus.microservices.club.objects.apiTypes.{Club as ClubResponse}
import upickle.default.*

final case class AddClubMemberAPIMessage(
    clubId: String,
    playerId: String,
    operatorId: Option[String] = None
) extends APIMessage[ClubResponse] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[ClubResponse] =
    for
      actor <- IO(resolveOperatorActor(context))
      occurredAt <- IO.realTimeInstant
      module = context.support.clubModule
      command = AddClubMemberCommand(
        clubId = ClubId(clubId),
        playerId = PlayerId(playerId),
        actor = actor,
        occurredAt = occurredAt
      )
      club <- IO {
        module.transactionManager
          .inTransaction {
            addClubMember(module, command)
          }
          .getOrElse(throw NoSuchElementException("Resource not found"))
      }
    yield ClubResponse.fromDomain(club)

  private def resolveOperatorActor(context: ApiPlanContext): AccessPrincipal =
    operatorId.filter(_.nonEmpty)
      .map(id => context.support.principal(PlayerId(id)))
      .getOrElse(AccessPrincipal.system)

  private def addClubMember(
      module: ClubModuleContext,
      command: AddClubMemberCommand
  ): Option[Club] =
    for
      club <- module.clubRepository.findById(command.clubId)
      player <- module.playerRepository.findById(command.playerId)
    yield
      ensureClubActive(club)
      requireActivePlayer(player, s"Player ${command.playerId.value} cannot join club ${command.clubId.value}")
      requireClubCapability(
        module = module,
        actor = command.actor,
        club = club,
        permission = Permission.ManageClubMembership,
        delegatedPrivileges = Set(ClubPrivilege.ApproveRoster)
      )

      val savedPlayer = module.playerRepository.save(player.joinClub(command.clubId))
      ClubProjectionRefresher.ensurePlayerDashboard(module, savedPlayer.id, command.occurredAt)
      module.clubRepository.save(
        ClubProjectionRefresher.refreshClubProjection(module, club.addMember(command.playerId), command.occurredAt)
      )

  private def ensureClubActive(club: Club): Unit =
    if club.dissolvedAt.nonEmpty then
      throw IllegalArgumentException(s"Club ${club.id.value} has already been dissolved")

  private def requireActivePlayer(player: Player, context: String): Unit =
    if player.status != PlayerStatus.Active then
      throw IllegalArgumentException(context)

  private def requireClubCapability(
      module: ClubModuleContext,
      actor: AccessPrincipal,
      club: Club,
      permission: Permission,
      delegatedPrivileges: Set[String]
  ): Unit =
    val authorizationService = module.authorizationService
    val hasBasePermission = authorizationService.can(actor, permission, clubId = Some(club.id))
    val hasDelegatedPrivilege = actor.playerId.exists { playerId =>
      club.members.contains(playerId) &&
        delegatedPrivileges.exists(privilege => club.hasPrivilege(playerId, privilege))
    }

    if !hasBasePermission && !hasDelegatedPrivilege then
      throw AuthorizationFailure(
        s"${actor.displayName} is not allowed to perform $permission in club ${club.id.value}"
      )

  private final case class AddClubMemberCommand(
      clubId: ClubId,
      playerId: PlayerId,
      actor: AccessPrincipal,
      occurredAt: Instant
  )
