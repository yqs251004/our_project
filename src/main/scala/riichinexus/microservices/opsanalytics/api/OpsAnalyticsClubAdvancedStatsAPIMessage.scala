package riichinexus.microservices.opsanalytics.api
import riichinexus.microservices.auth.objects.Permission
import riichinexus.microservices.auth.api.`private`.ResolveAccessPrincipalPrivateAPIMessage
import riichinexus.microservices.auth.api.AuthCheckPermissionAPIMessage

import java.util.NoSuchElementException

import cats.effect.IO
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.microservices.club.objects.clubmanagement.ClubId
import riichinexus.system.api.AuthorizationFailure
import riichinexus.microservices.auth.objects.`private`.AccessPrincipalPrivateView

import riichinexus.system.json.JsonCodecs.given
import riichinexus.microservices.opsanalytics.objects.{AdvancedStatsBoard, DashboardOwner}
import riichinexus.microservices.opsanalytics.tables.advancedstatsboard.AdvancedStatsBoardTable
import upickle.default.ReadWriter

/** 获取俱乐部高级统计面板。 */
final case class OpsAnalyticsClubAdvancedStatsAPIMessage(
    clubId: ClubId,
    operatorId: PlayerId
) extends APIMessage[AdvancedStatsBoard] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[AdvancedStatsBoard] =
    for
      operator <- ResolveAccessPrincipalPrivateAPIMessage(operatorId).plan(context)
      _ <- requireDashboardPermission(context, operator)
      board <- IO.blocking(findAdvancedStatsBoard(context))
    yield board

  private def requireDashboardPermission(context: ApiPlanContext, operator: AccessPrincipalPrivateView): IO[Unit] =
    AuthCheckPermissionAPIMessage(
      operatorId = operator.playerId.map(_.value),
      permission = Permission.ViewClubDashboard,
      clubId = Some(clubId.value)
    ).plan(context).flatMap { allowed =>
      if allowed then IO.unit
      else IO.raiseError(AuthorizationFailure(s"${operator.displayName} is not allowed to view advanced stats for club ${clubId.value}"))
    }

  private def findAdvancedStatsBoard(context: ApiPlanContext): AdvancedStatsBoard =
    AdvancedStatsBoardTable
      .findByOwner(context.connection, DashboardOwner.Club(clubId))
      .getOrElse(throw NoSuchElementException(s"Advanced stats board for club ${clubId.value} was not found"))
