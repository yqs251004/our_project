package riichinexus.microservices.opsanalytics.tables.dashboard

import java.sql.{Connection, ResultSet}

import scala.annotation.tailrec
import scala.util.Using

import riichinexus.system.errors.OptimisticConcurrencyException
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
import riichinexus.system.json.JsonCodecs.given
import riichinexus.microservices.opsanalytics.domain.functions.DashboardFunctions
import riichinexus.microservices.opsanalytics.objects.{Dashboard, DashboardOwner}
import upickle.default.{read, write}

object DashboardTable:
  private val upsertSql: String =
    """
      |insert into dashboards (owner_key, owner_type, payload, updated_at)
      |values (?, ?, cast(? as jsonb), now())
      |on conflict (owner_key) do update set
      |  owner_type = excluded.owner_type,
      |  payload = excluded.payload,
      |  updated_at = now()
      |where cast(dashboards.payload ->> 'version' as integer) = ?
      |""".stripMargin

  private[opsanalytics] def save(connection: Connection, dashboard: Dashboard): Dashboard =
    val persisted = dashboard.copy(version = dashboard.version + 1)
    val rowsUpdated = Using.resource(connection.prepareStatement(upsertSql)) { statement =>
      statement.setString(1, DashboardFunctions.ownerKey(persisted.owner))
      statement.setString(2, DashboardFunctions.ownerType(persisted.owner))
      statement.setString(3, write[Dashboard](persisted))
      statement.setInt(4, dashboard.version)
      statement.executeUpdate()
    }
    if rowsUpdated == 0 then
      throw OptimisticConcurrencyException(
        aggregateType = "dashboard",
        aggregateId = DashboardFunctions.ownerKey(persisted.owner),
        expectedVersion = dashboard.version,
        actualVersion = findByOwner(connection, persisted.owner).map(_.version)
      )
    persisted

  private val findByOwnerSql: String =
    """
      |select payload
      |from dashboards
      |where owner_key = ?
      |""".stripMargin

  private[opsanalytics] def findByOwner(connection: Connection, owner: DashboardOwner): Option[Dashboard] =
    Using.resource(connection.prepareStatement(findByOwnerSql)) { statement =>
      statement.setString(1, DashboardFunctions.ownerKey(owner))
      Using.resource(statement.executeQuery()) { resultSet =>
        if resultSet.next() then Some(readDashboard(resultSet))
        else None
      }
    }

  private val findAllSql: String =
    """
      |select payload
      |from dashboards
      |order by owner_key
      |""".stripMargin

  private[opsanalytics] def findAll(connection: Connection): Vector[Dashboard] =
    Using.resource(connection.prepareStatement(findAllSql)) { statement =>
      Using.resource(statement.executeQuery())(readDashboards)
    }

  private def readDashboards(resultSet: ResultSet): Vector[Dashboard] =
    @tailrec
    def loop(acc: Vector[Dashboard]): Vector[Dashboard] =
      if resultSet.next() then loop(readDashboard(resultSet) +: acc)
      else acc.reverse

    loop(Vector.empty)

  private def readDashboard(resultSet: ResultSet): Dashboard =
    read[Dashboard](resultSet.getString("payload"))
