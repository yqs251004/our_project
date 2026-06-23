package riichinexus.microservices.club.api.profile
import riichinexus.microservices.club.domain.profile.functions.ClubViewFunctions
import riichinexus.microservices.auth.api.authorization.`private`.CheckSuperAdminPrivateAPIMessage
import riichinexus.microservices.auth.api.authorization.`private`.ResolveAccessPrincipalPrivateAPIMessage
import riichinexus.microservices.player.api.`private`.RecordPlayerClubAdminGrantPrivateAPIMessage
import riichinexus.microservices.player.api.`private`.RecordPlayerClubJoinPrivateAPIMessage
import riichinexus.microservices.player.api.`private`.ResolvePlayerPrivateAPIMessage

import riichinexus.microservices.club.domain.profile.functions.ClubFunctions
import java.time.Instant
import java.util.NoSuchElementException

import cats.effect.IO
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.microservices.player.objects.PlayerId
import riichinexus.microservices.club.domain.profile.functions.ClubIdGenerator
import riichinexus.microservices.auth.objects.authorization.`private`.AccessPrincipalPrivateView
import riichinexus.microservices.club.domain.profile.model.Club
import riichinexus.microservices.player.objects.`private`.PlayerPrivateView
import riichinexus.microservices.player.objects.PlayerStatus
import riichinexus.system.api.AuthorizationFailure

import riichinexus.microservices.club.domain.profile.functions.ClubAuthorization
import riichinexus.microservices.club.domain.profile.functions.ClubProjectionRefresher
import riichinexus.microservices.club.objects.profile.ClubView
/** 创建俱乐部。 */
final case class CreateClubAPIMessage(
    name: String,
    creatorId: String
) extends APIMessage[ClubView]:

  override def plan(context: ApiPlanContext): IO[ClubView] =
    for
      creatorPlayerId <- IO.blocking(PlayerId(creatorId))
      actor <- ResolveAccessPrincipalPrivateAPIMessage(creatorPlayerId).plan(context)
      createdAt <- IO.realTimeInstant
      creator <- resolveCreatorPlayer(context, creatorPlayerId)
      _ <- IO.blocking {
        requireActivePlayer(creator, s"PlayerPrivateView ${creatorPlayerId.value} cannot create a club")
      }
      _ <- ensureCreatorCanCreateClub(context, actor, creatorPlayerId)
      clubToCreate <- IO.blocking(resolveClubToCreate(context.connection, name, creatorPlayerId, createdAt))
      _ <- RecordPlayerClubJoinPrivateAPIMessage(creator.id, clubToCreate.id).plan(context)
      savedCreator <- grantCreatorClubAdmin(context, creator, clubToCreate, actor, createdAt)
      _ <- ClubProjectionRefresher.ensurePlayerDashboard(context, savedCreator.id, createdAt)
      savedClub <- refreshAndSaveClub(context, clubToCreate, createdAt)
    yield ClubViewFunctions.clubView(savedClub)

  private def resolveCreatorPlayer(context: ApiPlanContext, creatorId: PlayerId): IO[PlayerPrivateView] =
    ResolvePlayerPrivateAPIMessage(creatorId).plan(context)
      .map(_.getOrElse(throw NoSuchElementException(s"PlayerPrivateView ${creatorId.value} was not found")))

  private def resolveClubToCreate(
      connection: java.sql.Connection,
      name: String,
      creatorId: PlayerId,
      createdAt: Instant
  ): Club =
    val normalizedName = name.trim
    require(normalizedName.nonEmpty, "Club name cannot be empty")
    riichinexus.microservices.club.tables.clubs.ClubTable.findByName(connection, normalizedName) match
      case Some(existing) =>
        ClubAuthorization.ensureClubActive(existing)
        ClubFunctions.grantAdmin(
          ClubFunctions.addMember(existing, creatorId),
          creatorId
        )
      case None =>
        Club(
          id = ClubIdGenerator.clubId(),
          name = normalizedName,
          creator = creatorId,
          createdAt = createdAt,
          members = Vector(creatorId),
          admins = Vector(creatorId)
        )

  private def grantCreatorClubAdmin(
      context: ApiPlanContext,
      creator: PlayerPrivateView,
      club: Club,
      actor: AccessPrincipalPrivateView,
      createdAt: Instant
  ): IO[PlayerPrivateView] =
    RecordPlayerClubAdminGrantPrivateAPIMessage(
      creator.id,
      club.id,
      createdAt,
      actor.playerId
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
