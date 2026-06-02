package riichinexus.microservices.club.domain
import riichinexus.microservices.player.domain.functions.PlayerPersistenceFunctions

import riichinexus.microservices.club.domain.clubmanagement.functions.ClubFunctions
import java.sql.Connection
import java.time.Instant

import cats.effect.unsafe.implicits.global
import riichinexus.system.api.ApiPlanContext
import riichinexus.microservices.player.domain.functions.PlayerIdGenerator
import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.microservices.club.domain.functions.ClubIdGenerator
import riichinexus.microservices.club.objects.clubmanagement.ClubId
import riichinexus.microservices.club.objects.membershipmanagement.MembershipApplicationId
import riichinexus.microservices.tournament.domain.functions.TournamentIdGenerator
import riichinexus.microservices.tournament.objects.lineupmanagement.LineupSubmissionId
import riichinexus.microservices.tournament.objects.paifumanagement.PaifuId
import riichinexus.microservices.tournament.objects.recordmanagement.MatchRecordId
import riichinexus.microservices.tournament.objects.settlementmanagement.SettlementSnapshotId
import riichinexus.microservices.tournament.objects.tablemanagement.TableId
import riichinexus.microservices.tournament.objects.tournamentmanagement.{TournamentId, TournamentStageId}
import riichinexus.microservices.tournament.appeal.domain.functions.AppealIdGenerator
import riichinexus.microservices.tournament.appeal.objects.ticketmanagement.AppealTicketId
import riichinexus.microservices.auth.domain.functions.AuthIdGenerator
import riichinexus.microservices.auth.objects.sessionmanagement.GuestSessionId
import riichinexus.microservices.audit.domain.functions.AuditIdGenerator
import riichinexus.microservices.audit.domain.auditevent.AuditEventId
import riichinexus.microservices.opsanalytics.domain.functions.OpsAnalyticsIdGenerator
import riichinexus.microservices.opsanalytics.objects.advancedstats.AdvancedStatsRecomputeTaskId
import riichinexus.microservices.club.domain.Club
import riichinexus.microservices.club.domain.clubmanagement.model.*
import riichinexus.microservices.club.domain.membershipmanagement.model.*
import riichinexus.microservices.club.domain.rankprivilegemanagement.model.*
import riichinexus.microservices.club.domain.relationmanagement.model.*
import riichinexus.microservices.player.domain.Player
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

  def refreshClubProjection(connection: Connection, club: Club, at: Instant): Club =
    val refreshedClub = ClubFunctions.updatePowerRating(club,
      ClubPowerRatingService.calculate(club, findPlayer(connection))
    )
    RecordClubDashboardAPIMessage(refreshedClub, at)
      .plan(apiContext(connection))
      .unsafeRunSync()
    refreshedClub

  private def findPlayer(connection: Connection)(playerId: PlayerId): Option[Player] =
    PlayerPersistenceFunctions.findPlayer(connection, playerId)

  private def apiContext(connection: Connection): ApiPlanContext =
    ApiPlanContext(bearerToken = None, connection = connection)
