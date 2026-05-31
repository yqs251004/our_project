package riichinexus.microservices.club.domain

import java.sql.Connection
import java.time.Instant

import cats.effect.unsafe.implicits.global
import riichinexus.api.ApiPlanContext
import riichinexus.bootstrap.ClubModuleContext
import riichinexus.domain.model.*
import riichinexus.microservices.club.domain.model.*
import riichinexus.microservices.player.objects.*
import riichinexus.microservices.opsanalytics.api.`private`.{
  EnsurePlayerDashboardAPIMessage,
  RecordClubDashboardAPIMessage
}
import riichinexus.microservices.player.api.{CreatePlayerAPIMessage, GetPlayerAPIMessage, ListPlayersAPIMessage}

object ClubProjectionRefresher:
  def ensurePlayerDashboard(connection: Connection, playerId: PlayerId, at: Instant): Unit =
    EnsurePlayerDashboardAPIMessage(playerId, at)
      .plan(apiContext(connection))
      .unsafeRunSync()

  def refreshClubProjection(connection: Connection, module: ClubModuleContext, club: Club, at: Instant): Club =
    val refreshedClub = club.updatePowerRating(
      ClubPowerRatingService.calculate(club, findPlayer(connection))
    )
    RecordClubDashboardAPIMessage(refreshedClub, at)
      .plan(apiContext(connection))
      .unsafeRunSync()
    refreshedClub

  private def findPlayer(connection: Connection)(playerId: PlayerId): Option[Player] =
    GetPlayerAPIMessage.findPlayer(connection, playerId)

  private def apiContext(connection: Connection): ApiPlanContext =
    ApiPlanContext(support = null, bearerToken = None, connection = connection)
