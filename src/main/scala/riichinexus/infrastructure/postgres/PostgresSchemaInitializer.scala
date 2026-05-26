package riichinexus.infrastructure.postgres

import java.sql.Connection

import scala.util.Using

import riichinexus.microservices.auth.tables.accountcredential.AccountCredentialTableInitializer
import riichinexus.microservices.auth.tables.authenticatedsession.AuthenticatedSessionTableInitializer
import riichinexus.microservices.auth.tables.guestsession.GuestSessionTableInitializer
import riichinexus.microservices.club.tables.clubaudit.ClubContributionAuditTableInitializer
import riichinexus.microservices.club.tables.club.ClubTableInitializer
import riichinexus.microservices.dictionary.tables.dictionarynamespace.DictionaryNamespaceTableInitializer
import riichinexus.microservices.dictionary.tables.globaldictionary.GlobalDictionaryTableInitializer
import riichinexus.microservices.opsanalytics.tables.advancedstatsboard.AdvancedStatsBoardTableInitializer
import riichinexus.microservices.opsanalytics.tables.advancedstatsrecomputetask.AdvancedStatsRecomputeTaskTableInitializer
import riichinexus.infrastructure.audit.tables.auditevent.AuditEventTableInitializer
import riichinexus.microservices.opsanalytics.tables.dashboard.DashboardTableInitializer
import riichinexus.infrastructure.events.tables.domaineventdeliveryreceipt.DomainEventDeliveryReceiptTableInitializer
import riichinexus.infrastructure.events.tables.domaineventoutbox.DomainEventOutboxTableInitializer
import riichinexus.infrastructure.events.tables.domaineventsubscribercursor.DomainEventSubscriberCursorTableInitializer
import riichinexus.infrastructure.events.tables.eventcascaderecord.EventCascadeRecordTableInitializer
import riichinexus.microservices.player.tables.player.PlayerTableInitializer
import riichinexus.microservices.tournament.appeal.tables.appealticket.AppealTicketTableInitializer
import riichinexus.microservices.tournament.tables.matchrecord.MatchRecordTableInitializer
import riichinexus.microservices.tournament.tables.paifu.PaifuTableInitializer
import riichinexus.microservices.tournament.tables.settlement.TournamentSettlementTableInitializer
import riichinexus.microservices.tournament.tables.tournament.TournamentTableInitializer
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
      GlobalDictionaryTableInitializer.initialize(connection)
      DictionaryNamespaceTableInitializer.initialize(connection)
      PlayerTableInitializer.initialize(connection)
      DashboardTableInitializer.initialize(connection)
      AdvancedStatsBoardTableInitializer.initialize(connection)
      AdvancedStatsRecomputeTaskTableInitializer.initialize(connection)
      EventCascadeRecordTableInitializer.initialize(connection)
      DomainEventOutboxTableInitializer.initialize(connection)
      DomainEventDeliveryReceiptTableInitializer.initialize(connection)
      DomainEventSubscriberCursorTableInitializer.initialize(connection)
      AuditEventTableInitializer.initialize(connection)
      ClubContributionAuditTableInitializer.initialize(connection)
      TournamentTableInitializer.initialize(connection)
      TournamentGameTableInitializer.initialize(connection)
      PaifuTableInitializer.initialize(connection)
      MatchRecordTableInitializer.initialize(connection)
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
