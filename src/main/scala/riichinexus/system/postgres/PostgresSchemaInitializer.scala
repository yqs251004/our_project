package riichinexus.system.postgres

import java.sql.Connection

import scala.util.Using

import riichinexus.microservices.auth.tables.accountcredential.AccountCredentialTableInitializer
import riichinexus.microservices.auth.tables.authenticatedsession.AuthenticatedSessionTableInitializer
import riichinexus.microservices.auth.tables.guestsession.GuestSessionTableInitializer
import riichinexus.microservices.club.tables.clubs.ClubTableInitializer
import riichinexus.microservices.opsanalytics.tables.advancedstatsboard.AdvancedStatsBoardTableInitializer
import riichinexus.microservices.opsanalytics.tables.advancedstatsrecomputetask.AdvancedStatsRecomputeTaskTableInitializer
import riichinexus.microservices.audit.tables.auditevent.AuditEventTableInitializer
import riichinexus.microservices.opsanalytics.tables.dashboard.DashboardTableInitializer
import riichinexus.microservices.player.tables.players.PlayerTableInitializer
import riichinexus.microservices.tournament.appeal.tables.appealticket.AppealTicketTableInitializer
import riichinexus.microservices.tournament.mahjongcore.tables.tablestate.MahjongTableStateTableInitializer
import riichinexus.microservices.tournament.tables.matchrecord.MatchRecordTableInitializer
import riichinexus.microservices.tournament.tables.paifu.PaifuTableInitializer
import riichinexus.microservices.tournament.tables.settlement.TournamentSettlementTableInitializer
import riichinexus.microservices.tournament.tables.tournaments.TournamentTableInitializer
import riichinexus.microservices.tournament.tables.tournamentgame.TournamentGameTableInitializer

final class PostgresSchemaInitializer(connectionFactory: JdbcConnectionFactory):
  def initialize(): Unit =
    connectionFactory.withConnection { connection =>
      PostgresSchemaDefinitions.statements.foreach { sql =>
        execute(connection, sql)
      }
      GuestSessionTableInitializer.initialize(connection)
      AccountCredentialTableInitializer.initialize(connection)
      AuthenticatedSessionTableInitializer.initialize(connection)
      ClubTableInitializer.initialize(connection)
      PlayerTableInitializer.initialize(connection)
      DashboardTableInitializer.initialize(connection)
      AdvancedStatsBoardTableInitializer.initialize(connection)
      AdvancedStatsRecomputeTaskTableInitializer.initialize(connection)
      AuditEventTableInitializer.initialize(connection)
      TournamentTableInitializer.initialize(connection)
      TournamentGameTableInitializer.initialize(connection)
      PaifuTableInitializer.initialize(connection)
      MatchRecordTableInitializer.initialize(connection)
      MahjongTableStateTableInitializer.initialize(connection)
      AppealTicketTableInitializer.initialize(connection)
      TournamentSettlementTableInitializer.initialize(connection)
    }

  private def execute(connection: Connection, sql: String): Unit =
    Using.resource(connection.createStatement()) { statement =>
      statement.execute(sql)
    }

object PostgresSchemaInitializer:
  def apply(connectionFactory: JdbcConnectionFactory): PostgresSchemaInitializer =
    new PostgresSchemaInitializer(connectionFactory)
