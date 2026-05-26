package riichinexus.microservices.club.api

import java.time.Instant
import java.util.NoSuchElementException

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.bootstrap.ClubModuleContext
import riichinexus.domain.model.*
import riichinexus.microservices.club.domain.model.*
import riichinexus.microservices.player.objects.*
import riichinexus.domain.service.*
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.club.domain.{ClubAuthorization, ClubProjectionRefresher}
import riichinexus.microservices.club.objects.ClubView
import riichinexus.microservices.player.tables.player.PlayerTable
import upickle.default.*

final case class CreateClubAPIMessage(
    name: String,
    creatorId: String
) extends APIMessage[ClubView] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[ClubView] =
    for
      parsedCreatorId <- IO(PlayerId(creatorId))
      actor <- IO(context.principal(parsedCreatorId))
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
          createClub(context.connection, module, command)
        }
      }
    yield ClubView.fromDomain(club)

  private def createClub(connection: java.sql.Connection, module: ClubModuleContext, command: CreateClubCommand): Club =
    val normalizedName = command.name.trim
    require(normalizedName.nonEmpty, "Club name cannot be empty")

    val creator = PlayerTable
      .findById(connection, command.creatorId)
      .getOrElse(throw NoSuchElementException(s"Player ${command.creatorId.value} was not found"))
    requireActivePlayer(creator, s"Player ${command.creatorId.value} cannot create a club")
    ensureCreatorCanCreateClub(command.actor, command.creatorId)

    val club = resolveClubToCreate(connection, normalizedName, command.creatorId, command.createdAt)
    val updatedCreator = creator
      .joinClub(club.id)
      .grantRole(RoleGrant.clubAdmin(club.id, command.createdAt, command.actor.playerId))

    val savedCreator = PlayerTable.save(connection, updatedCreator)
    ClubProjectionRefresher.ensurePlayerDashboard(connection, savedCreator.id, command.createdAt)
    riichinexus.microservices.club.tables.club.ClubTable.save(connection, ClubProjectionRefresher.refreshClubProjection(connection, module, club, command.createdAt))

  private def resolveClubToCreate(
      connection: java.sql.Connection,
      normalizedName: String,
      creatorId: PlayerId,
      createdAt: Instant
  ): Club =
    riichinexus.microservices.club.tables.club.ClubTable.findByName(connection, normalizedName) match
      case Some(existing) =>
        ClubAuthorization.ensureClubActive(existing)
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

  private def requireActivePlayer(player: Player, context: String): Unit =
    if player.status != PlayerStatus.Active then
      throw IllegalArgumentException(context)

  private final case class CreateClubCommand(
      name: String,
      creatorId: PlayerId,
      actor: AccessPrincipal,
      createdAt: Instant
  )
