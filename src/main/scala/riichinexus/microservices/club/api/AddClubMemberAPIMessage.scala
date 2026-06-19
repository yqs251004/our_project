package riichinexus.microservices.club.api
import riichinexus.microservices.auth.objects.Permission
import riichinexus.microservices.auth.api.`private`.ResolveAccessPrincipalPrivateAPIMessage
import riichinexus.microservices.auth.api.`private`.ResolveSystemAccessPrincipalPrivateAPIMessage
import riichinexus.microservices.player.api.`private`.{RecordPlayerClubJoinPrivateAPIMessage, ResolvePlayerPrivateAPIMessage}

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
import riichinexus.system.json.JsonCodecs.given
import riichinexus.microservices.club.domain.{ClubAuthorization, ClubProjectionRefresher}
import riichinexus.microservices.club.objects.rankprivilegemanagement.ClubPrivilegeCode
import riichinexus.microservices.club.objects.clubmanagement.ClubView
import upickle.default.ReadWriter

/** 向俱乐部添加成员。 */
final case class AddClubMemberAPIMessage(
    clubId: String,
    playerId: String,
    operatorId: Option[String] = None
) extends APIMessage[ClubView] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[ClubView] =
    for
      actor <- resolveOperatorActor(context)
      occurredAt <- IO.realTimeInstant
      command = AddClubMemberCommand(
        clubId = ClubId(clubId),
        playerId = PlayerId(playerId),
        actor = actor,
        occurredAt = occurredAt
      )
      club <- addClubMember(context, command).map(_.getOrElse(throw NoSuchElementException("Resource not found")))
    yield ClubView.fromDomain(club)

  private def resolveOperatorActor(context: ApiPlanContext): IO[AccessPrincipalPrivateView] =
    operatorId.filter(_.nonEmpty)
      .map(id => ResolveAccessPrincipalPrivateAPIMessage(PlayerId(id)).plan(context))
      .getOrElse(ResolveSystemAccessPrincipalPrivateAPIMessage().plan(context))

  private def addClubMember(
      context: ApiPlanContext,
      command: AddClubMemberCommand
  ): IO[Option[Club]] =
    val connection = context.connection
    for
      club <- IO.blocking(riichinexus.microservices.club.tables.clubs.ClubTable.findById(connection, command.clubId))
      player <- ResolvePlayerPrivateAPIMessage(command.playerId).plan(context)
        .map(_.getOrElse(throw NoSuchElementException(s"PlayerPrivateView ${command.playerId.value} was not found")))
      savedClub <- club match
        case None => IO.pure(None)
        case Some(club) =>
          ClubAuthorization.ensureClubActive(club)
          requireActivePlayer(player, s"PlayerPrivateView ${command.playerId.value} cannot join club ${command.clubId.value}")
          ClubAuthorization.requireClubCapability(actor = command.actor,
            club = club,
            permission = Permission.ManageClubMembership,
              delegatedPrivileges = Set(ClubPrivilegeCode.ApproveRoster)
          )
          for
            savedPlayer <- RecordPlayerClubJoinPrivateAPIMessage(command.playerId, command.clubId).plan(context).map(
              _.getOrElse(throw NoSuchElementException(s"PlayerPrivateView ${command.playerId.value} was not found"))
            )
            _ <- ClubProjectionRefresher.ensurePlayerDashboard(context, savedPlayer.id, command.occurredAt)
            refreshedClub <- ClubProjectionRefresher.refreshClubProjection(
              context,
              ClubFunctions.addMember(club, command.playerId),
              command.occurredAt
            )
            savedClub <- IO.blocking(riichinexus.microservices.club.tables.clubs.ClubTable.save(connection, refreshedClub))
          yield Some(savedClub)
    yield savedClub

  private def requireActivePlayer(player: PlayerPrivateView, context: String): Unit =
    if player.status != PlayerStatus.Active then
      throw IllegalArgumentException(context)

  private final case class AddClubMemberCommand(
      clubId: ClubId,
      playerId: PlayerId,
      actor: AccessPrincipalPrivateView,
      occurredAt: Instant
  )

