package riichinexus.microservices.club.api
import riichinexus.microservices.auth.api.`private`.{CheckSuperAdminPrivateAPIMessage, ResolveAccessPrincipalPrivateAPIMessage}
import riichinexus.microservices.player.api.`private`.{RecordPlayerClubAdminGrantPrivateAPIMessage, RecordPlayerClubJoinPrivateAPIMessage, ResolvePlayerPrivateAPIMessage}

import riichinexus.microservices.club.domain.clubmanagement.functions.ClubFunctions
import java.time.Instant
import java.util.NoSuchElementException

import cats.effect.IO
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.microservices.club.domain.functions.ClubIdGenerator
import riichinexus.microservices.auth.objects.`private`.AccessPrincipalPrivateView
import riichinexus.microservices.club.domain.Club
import riichinexus.microservices.player.objects.`private`.PlayerPrivateView
import riichinexus.microservices.player.objects.PlayerStatus
import riichinexus.system.api.AuthorizationFailure

import riichinexus.microservices.club.domain.{ClubAuthorization, ClubProjectionRefresher}
import riichinexus.microservices.club.objects.clubmanagement.ClubView
import upickle.default.ReadWriter

/** 创建俱乐部。 */
final case class CreateClubAPIMessage(
    name: String,
    creatorId: String
) extends APIMessage[ClubView] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[ClubView] =
    for
      parsedCreatorId <- IO.blocking(PlayerId(creatorId))
      actor <- ResolveAccessPrincipalPrivateAPIMessage(parsedCreatorId).plan(context)
      createdAt <- IO.realTimeInstant
      command = CreateClubCommand(
        name = name,
        creatorId = parsedCreatorId,
        actor = actor,
        createdAt = createdAt
      )
      creator <- resolveCreatorPlayer(context, command)
      _ <- IO.blocking {
        requireActivePlayer(creator, s"PlayerPrivateView ${command.creatorId.value} cannot create a club")
      }
      _ <- ensureCreatorCanCreateClub(context, command.actor, command.creatorId)
      clubToCreate <- IO.blocking(resolveClubToCreate(context.connection, command))
      _ <- RecordPlayerClubJoinPrivateAPIMessage(creator.id, clubToCreate.id).plan(context)
      savedCreator <- grantCreatorClubAdmin(context, creator, clubToCreate, command)
      _ <- ClubProjectionRefresher.ensurePlayerDashboard(context, savedCreator.id, command.createdAt)
      savedClub <- refreshAndSaveClub(context, clubToCreate, command.createdAt)
    yield ClubView.fromDomain(savedClub)

  private def resolveCreatorPlayer(context: ApiPlanContext, command: CreateClubCommand): IO[PlayerPrivateView] =
    ResolvePlayerPrivateAPIMessage(command.creatorId).plan(context)
      .map(_.getOrElse(throw NoSuchElementException(s"PlayerPrivateView ${command.creatorId.value} was not found")))

  private def resolveClubToCreate(
      connection: java.sql.Connection,
      command: CreateClubCommand
  ): Club =
    val normalizedName = command.name.trim
    require(normalizedName.nonEmpty, "Club name cannot be empty")
    riichinexus.microservices.club.tables.clubs.ClubTable.findByName(connection, normalizedName) match
      case Some(existing) =>
        ClubAuthorization.ensureClubActive(existing)
        ClubFunctions.grantAdmin(
          ClubFunctions.addMember(existing, command.creatorId),
          command.creatorId
        )
      case None =>
        Club(
          id = ClubIdGenerator.clubId(),
          name = normalizedName,
          creator = command.creatorId,
          createdAt = command.createdAt,
          members = Vector(command.creatorId),
          admins = Vector(command.creatorId)
        )

  private def grantCreatorClubAdmin(
      context: ApiPlanContext,
      creator: PlayerPrivateView,
      club: Club,
      command: CreateClubCommand
  ): IO[PlayerPrivateView] =
    RecordPlayerClubAdminGrantPrivateAPIMessage(
      creator.id,
      club.id,
      command.createdAt,
      command.actor.playerId
    ).plan(context).flatMap(_ =>
      ResolvePlayerPrivateAPIMessage(creator.id).plan(context).map(
        _.getOrElse(throw NoSuchElementException(s"Player ${creator.id.value} was not found"))
      )
    )

  private def refreshAndSaveClub(
      context: ApiPlanContext,
      club: Club,
      refreshedAt: Instant
  ): IO[Club] =
    for
      refreshedClub <- ClubProjectionRefresher.refreshClubProjection(context, club, refreshedAt)
      savedClub <- IO.blocking(riichinexus.microservices.club.tables.clubs.ClubTable.save(context.connection, refreshedClub))
    yield savedClub

  private def ensureCreatorCanCreateClub(context: ApiPlanContext, actor: AccessPrincipalPrivateView, creatorId: PlayerId): IO[Unit] =
    CheckSuperAdminPrivateAPIMessage(actor).plan(context).flatMap { isSuperAdmin =>
      if !isSuperAdmin && actor.playerId.exists(_ != creatorId) then
        IO.raiseError(AuthorizationFailure("Only the creator or a super admin can create the club"))
      else IO.unit
    }

  private def requireActivePlayer(player: PlayerPrivateView, context: String): Unit =
    if player.status != PlayerStatus.Active then
      throw IllegalArgumentException(context)

  private final case class CreateClubCommand(
      name: String,
      creatorId: PlayerId,
      actor: AccessPrincipalPrivateView,
      createdAt: Instant
  )
