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

final case class CreateClubAPIMessage(
    name: String,
    creatorId: String
) extends APIMessage[ClubResponse] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[ClubResponse] =
    for
      parsedCreatorId <- IO(PlayerId(creatorId))
      actor <- IO(context.support.principal(parsedCreatorId))
      createdAt <- IO.realTimeInstant
      module = context.support.clubModule
      command = CreateClubCommand(
        name = name,
        creatorId = parsedCreatorId,
        actor = actor,
        createdAt = createdAt
      )
      club <- IO {
        module.transactionManager.inTransaction {
          createClub(module, command)
        }
      }
    yield ClubResponse.fromDomain(club)

  private def createClub(module: ClubModuleContext, command: CreateClubCommand): Club =
    val normalizedName = command.name.trim
    require(normalizedName.nonEmpty, "Club name cannot be empty")

    val creator = module.playerRepository
      .findById(command.creatorId)
      .getOrElse(throw NoSuchElementException(s"Player ${command.creatorId.value} was not found"))
    requireActivePlayer(creator, s"Player ${command.creatorId.value} cannot create a club")
    ensureCreatorCanCreateClub(command.actor, command.creatorId)

    val club = resolveClubToCreate(module, normalizedName, command.creatorId, command.createdAt)
    val updatedCreator = creator
      .joinClub(club.id)
      .grantRole(RoleGrant.clubAdmin(club.id, command.createdAt, command.actor.playerId))

    val savedCreator = module.playerRepository.save(updatedCreator)
    ClubProjectionRefresher.ensurePlayerDashboard(module, savedCreator.id, command.createdAt)
    module.clubRepository.save(ClubProjectionRefresher.refreshClubProjection(module, club, command.createdAt))

  private def resolveClubToCreate(
      module: ClubModuleContext,
      normalizedName: String,
      creatorId: PlayerId,
      createdAt: Instant
  ): Club =
    module.clubRepository.findByName(normalizedName) match
      case Some(existing) =>
        ensureClubActive(existing)
        existing
          .addMember(creatorId)
          .grantAdmin(creatorId)
      case None =>
        Club(
          id = IdGenerator.clubId(),
          name = normalizedName,
          creator = creatorId,
          createdAt = createdAt,
          members = Vector(creatorId),
          admins = Vector(creatorId)
        )

  private def ensureCreatorCanCreateClub(actor: AccessPrincipal, creatorId: PlayerId): Unit =
    if !actor.isSuperAdmin && actor.playerId.exists(_ != creatorId) then
      throw AuthorizationFailure("Only the creator or a super admin can create the club")

  private def ensureClubActive(club: Club): Unit =
    if club.dissolvedAt.nonEmpty then
      throw IllegalArgumentException(s"Club ${club.id.value} has already been dissolved")

  private def requireActivePlayer(player: Player, context: String): Unit =
    if player.status != PlayerStatus.Active then
      throw IllegalArgumentException(context)

  private final case class CreateClubCommand(
      name: String,
      creatorId: PlayerId,
      actor: AccessPrincipal,
      createdAt: Instant
  )
