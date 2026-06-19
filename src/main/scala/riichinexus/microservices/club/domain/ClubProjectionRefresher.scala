package riichinexus.microservices.club.domain
import riichinexus.microservices.player.api.`private`.ResolvePlayersPrivateAPIMessage

import riichinexus.microservices.club.domain.clubmanagement.functions.ClubFunctions
import java.time.Instant

import cats.effect.IO
import riichinexus.system.api.ApiPlanContext
import riichinexus.microservices.player.objects.playerprofile.PlayerId

import riichinexus.microservices.opsanalytics.api.`private`.{EnsurePlayerDashboardPrivateAPIMessage, RecordClubDashboardPrivateAPIMessage}

/** ClubProjectionRefresher 编排俱乐部ProjectionRefresher 相关的领域流程和规则判断。 */

object ClubProjectionRefresher:
  def ensurePlayerDashboard(context: ApiPlanContext, playerId: PlayerId, at: Instant): IO[Unit] =
    EnsurePlayerDashboardPrivateAPIMessage(playerId, at)
      .plan(context)
      .map(_ => ())

  def refreshClubProjection(context: ApiPlanContext, club: Club, at: Instant): IO[Club] =
    for
      players <- ResolvePlayersPrivateAPIMessage(club.members.distinct).plan(context)
      refreshedClub = ClubFunctions.updatePowerRating(club,
        ClubPowerRatingService.calculate(club, players.map(player => player.id -> player).toMap.get)
      )
      _ <- RecordClubDashboardPrivateAPIMessage(refreshedClub.id, at).plan(context)
    yield refreshedClub
