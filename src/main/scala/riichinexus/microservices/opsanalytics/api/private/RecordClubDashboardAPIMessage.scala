package riichinexus.microservices.opsanalytics.api.`private`
import riichinexus.microservices.player.api.`private`.*

import cats.effect.IO
import java.time.Instant

import riichinexus.system.api.{APIMessage, ApiPlanContext}
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
import riichinexus.system.json.JsonCodecs.given
import riichinexus.microservices.club.domain.Club
import riichinexus.microservices.opsanalytics.domain.functions.DashboardFunctions
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
      activeMemberIds <- club.members.foldLeft(IO.pure(Vector.empty[PlayerId])) { (previous, playerId) =>
        previous.flatMap { ids =>
          ResolvePlayerPrivateAPIMessage(playerId).plan(context).map {
            case Some(player) if player.status == PlayerStatus.Active => ids :+ playerId
            case _                                                    => ids
          }
        }
      }
      memberDashboards <- IO.blocking {
        activeMemberIds.flatMap(playerId => DashboardTable.findByOwner(context.connection, DashboardOwner.Player(playerId)))
      }
      existingVersion <- IO.blocking {
        DashboardTable.findByOwner(context.connection, DashboardOwner.Club(club.id)).map(_.version).getOrElse(0)
      }
      dashboard <- IO.blocking {
        DashboardTable.save(
          context.connection,
          DashboardFunctions.buildClubDashboard(club, memberDashboards, at, existingVersion)
        )
      }
    yield dashboard
