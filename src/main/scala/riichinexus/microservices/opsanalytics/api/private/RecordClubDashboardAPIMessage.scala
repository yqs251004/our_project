package riichinexus.microservices.opsanalytics.api.`private`

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import java.time.Instant

import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.domain.model.PlayerId
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.club.domain.model.Club
import riichinexus.microservices.opsanalytics.objects.{Dashboard, DashboardOwner}
import riichinexus.microservices.opsanalytics.tables.dashboard.DashboardTable
import riichinexus.microservices.player.api.GetPlayerAPIMessage
import riichinexus.microservices.player.objects.PlayerStatus
import upickle.default.*

final case class RecordClubDashboardAPIMessage(
    club: Club,
    at: Instant
) extends APIMessage[Dashboard] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[Dashboard] =
    for
      dashboard <- IO.blocking(DashboardTable.save(context.connection, buildClubDashboard(context)))
    yield dashboard

  private def buildClubDashboard(context: ApiPlanContext): Dashboard =
    val existingVersion = DashboardTable.findByOwner(context.connection, DashboardOwner.Club(club.id)).map(_.version).getOrElse(0)
    val memberDashboards = club.members.flatMap { playerId =>
      findPlayerActive(context, playerId)
        .flatMap(_ => DashboardTable.findByOwner(context.connection, DashboardOwner.Player(playerId)))
    }

    if memberDashboards.isEmpty then Dashboard.empty(DashboardOwner.Club(club.id), at).copy(version = existingVersion)
    else
      Dashboard(
        owner = DashboardOwner.Club(club.id),
        sampleSize = memberDashboards.map(_.sampleSize).sum,
        dealInRate = weightedAverage(memberDashboards, _.dealInRate),
        winRate = weightedAverage(memberDashboards, _.winRate),
        averageWinPoints = weightedAverage(memberDashboards, _.averageWinPoints),
        riichiRate = weightedAverage(memberDashboards, _.riichiRate),
        averagePlacement = weightedAverage(memberDashboards, _.averagePlacement),
        topFinishRate = weightedAverage(memberDashboards, _.topFinishRate),
        lastUpdatedAt = at,
        version = existingVersion
      )

  private def findPlayerActive(context: ApiPlanContext, playerId: PlayerId): Option[Unit] =
    GetPlayerAPIMessage(playerId.value)
      .plan(context)
      .attempt
      .unsafeRunSync()
      .toOption
      .filter(_.status == PlayerStatus.Active.toString)
      .map(_ => ())

  private def weightedAverage(dashboards: Vector[Dashboard], selector: Dashboard => Double): Double =
    val totalWeight = dashboards.map(_.sampleSize).sum
    if totalWeight <= 0 then 0.0
    else BigDecimal(dashboards.map(dashboard => selector(dashboard) * dashboard.sampleSize).sum / totalWeight.toDouble)
      .setScale(2, BigDecimal.RoundingMode.HALF_UP)
      .toDouble
