package riichinexus.microservices.club.api
import riichinexus.microservices.auth.objects.Permission
import riichinexus.microservices.auth.api.`private`.ResolveAccessPrincipalPrivateAPIMessage
import riichinexus.microservices.player.api.`private`.{RecordPlayerClubAdminGrantPrivateAPIMessage, ResolvePlayerPrivateAPIMessage}

import riichinexus.microservices.club.domain.clubmanagement.functions.ClubFunctions
import java.time.Instant
import java.util.NoSuchElementException

import cats.effect.IO
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.microservices.club.objects.clubmanagement.ClubId
import riichinexus.microservices.auth.objects.`private`.AccessPrincipalPrivateView
import riichinexus.microservices.club.domain.Club
import riichinexus.microservices.player.objects.`private`.PlayerPrivateView
import riichinexus.microservices.player.objects.PlayerStatus

import riichinexus.microservices.club.domain.ClubAuthorization
import riichinexus.microservices.club.objects.clubmanagement.ClubView
import upickle.default.ReadWriter

/** 授予玩家俱乐部管理员身份。 */
final case class AssignClubAdminAPIMessage(
    clubId: String,
    playerId: String,
    operatorId: String
) extends APIMessage[ClubView] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[ClubView] =
    for
      actor <- ResolveAccessPrincipalPrivateAPIMessage(PlayerId(operatorId)).plan(context)
      grantedAt <- IO.realTimeInstant
      command = AssignClubAdminCommand(
        clubId = ClubId(clubId),
        playerId = PlayerId(playerId),
        actor = actor,
        grantedAt = grantedAt
      )
      club <- assignAdmin(context, command).map(_.getOrElse(throw NoSuchElementException("Resource not found")))
    yield ClubView.fromDomain(club)

  private def assignAdmin(
      context: ApiPlanContext,
      command: AssignClubAdminCommand
  ): IO[Option[Club]] =
    val connection = context.connection
    for
      club <- IO.blocking(riichinexus.microservices.club.tables.clubs.ClubTable.findById(connection, command.clubId))
      player <- ResolvePlayerPrivateAPIMessage(command.playerId).plan(context)
        .map(_.getOrElse(throw NoSuchElementException(s"PlayerPrivateView ${command.playerId.value} was not found")))
      savedClub <- club match
        case None => IO.pure(None)
        case Some(club) =>
          ensureAdminCanBeAssigned(club, player, command)
          for
            _ <- RecordPlayerClubAdminGrantPrivateAPIMessage(
              command.playerId,
              command.clubId,
              command.grantedAt,
              command.actor.playerId
            ).plan(context)
            savedClub <- IO.blocking(riichinexus.microservices.club.tables.clubs.ClubTable.save(connection, ClubFunctions.grantAdmin(club, command.playerId)))
          yield Some(savedClub)
    yield savedClub

  private def ensureAdminCanBeAssigned(
      club: Club,
      player: PlayerPrivateView,
      command: AssignClubAdminCommand
  ): Unit =
    ClubAuthorization.ensureClubActive(club)
    requireActivePlayer(player, s"PlayerPrivateView ${command.playerId.value} cannot be granted club admin")
    ClubAuthorization.requireClubMember(club, command.playerId, "assign club admin")
    ClubAuthorization.requireClubAdmin(actor = command.actor,
      club = club,
      permission = Permission.AssignClubAdmin
    )

  private def requireActivePlayer(player: PlayerPrivateView, context: String): Unit =
    if player.status != PlayerStatus.Active then
      throw IllegalArgumentException(context)

  private final case class AssignClubAdminCommand(
      clubId: ClubId,
      playerId: PlayerId,
      actor: AccessPrincipalPrivateView,
      grantedAt: Instant
  )
