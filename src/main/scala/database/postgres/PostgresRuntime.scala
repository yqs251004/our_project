package database.postgres

import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException

import scala.util.Using

import ports.TransactionManager

final class JdbcConnectionFactory(config: DatabaseConfig):
  private val driverClass = "org.postgresql.Driver"
  private val currentConnection = ThreadLocal[Connection]()

  try Class.forName(driverClass)
  catch
    case _: ClassNotFoundException =>
      ()

  def withConnection[A](f: Connection => A): A =
    Option(currentConnection.get()) match
      case Some(connection) =>
        f(connection)
      case None =>
        openConnection(f)

  def inTransaction[A](operation: => A): A =
    Option(currentConnection.get()) match
      case Some(_) =>
        operation
      case None =>
        openConnection { connection =>
          val previousAutoCommit = connection.getAutoCommit
          connection.setAutoCommit(false)
          currentConnection.set(connection)
          try
            val result = operation
            connection.commit()
            result
          catch
            case error: Throwable =>
              connection.rollback()
              throw error
          finally
            currentConnection.remove()
            connection.setAutoCommit(previousAutoCommit)
        }

  private def openConnection[A](f: Connection => A): A =
    try
      Using.resource(DriverManager.getConnection(config.url, config.user, config.password)) { connection =>
        connection.setSchema(config.schema)
        f(connection)
      }
    catch
      case error: SQLException if error.getMessage != null && error.getMessage.contains("No suitable driver") =>
        throw IllegalStateException(
          "PostgreSQL JDBC driver is not available. Run sbt once to download dependencies.",
          error
        )

object JdbcConnectionFactory:
  def apply(config: DatabaseConfig): JdbcConnectionFactory =
    new JdbcConnectionFactory(config)

final class JdbcTransactionManager(
    connectionFactory: JdbcConnectionFactory
) extends TransactionManager:
  override def inTransaction[A](operation: => A): A =
    connectionFactory.inTransaction(operation)

object JdbcTransactionManager:
  def apply(connectionFactory: JdbcConnectionFactory): JdbcTransactionManager =
    new JdbcTransactionManager(connectionFactory)

final class PostgresSchemaInitializer(connectionFactory: JdbcConnectionFactory):
  def initialize(): Unit =
    connectionFactory.withConnection { connection =>
      execute(
        connection,
        """
          |create table if not exists schema_version (
          |  version integer primary key,
          |  description text not null,
          |  applied_at timestamptz not null default now()
          |)
          |""".stripMargin
      )
      execute(
        connection,
        """
          |create table if not exists players (
          |  id text primary key,
          |  user_id text not null,
          |  nickname text not null,
          |  club_id text null,
          |  elo integer not null,
          |  payload jsonb not null,
          |  updated_at timestamptz not null default now()
          |)
          |""".stripMargin
      )
      execute(connection, "alter table players add column if not exists user_id text")
      execute(connection, "alter table players add column if not exists nickname text")
      execute(connection, "alter table players add column if not exists club_id text")
      execute(connection, "alter table players add column if not exists elo integer")
      execute(connection, "alter table players add column if not exists payload jsonb")
      execute(connection, "alter table players add column if not exists updated_at timestamptz default now()")
      execute(connection, "create unique index if not exists idx_players_user_id on players (user_id)")
      execute(connection, "create index if not exists idx_players_club_id on players (club_id)")

      execute(
        connection,
        """
          |create table if not exists clubs (
          |  id text primary key,
          |  name text not null,
          |  creator_id text not null,
          |  total_points integer not null,
          |  payload jsonb not null,
          |  updated_at timestamptz not null default now()
          |)
          |""".stripMargin
      )
      execute(connection, "alter table clubs add column if not exists name text")
      execute(connection, "alter table clubs add column if not exists creator_id text")
      execute(connection, "alter table clubs add column if not exists total_points integer")
      execute(connection, "alter table clubs add column if not exists payload jsonb")
      execute(connection, "alter table clubs add column if not exists updated_at timestamptz default now()")
      execute(connection, "create unique index if not exists idx_clubs_name on clubs (name)")

      execute(
        connection,
        """
          |create table if not exists tournaments (
          |  id text primary key,
          |  name text not null,
          |  organizer text not null,
          |  status text not null,
          |  payload jsonb not null,
          |  updated_at timestamptz not null default now()
          |)
          |""".stripMargin
      )
      execute(connection, "alter table tournaments add column if not exists name text")
      execute(connection, "alter table tournaments add column if not exists organizer text")
      execute(connection, "alter table tournaments add column if not exists status text")
      execute(connection, "alter table tournaments add column if not exists payload jsonb")
      execute(connection, "alter table tournaments add column if not exists updated_at timestamptz default now()")
      execute(
        connection,
        "create unique index if not exists idx_tournaments_name_start on tournaments (name, organizer)"
      )

      execute(
        connection,
        """
          |create table if not exists tables (
          |  id text primary key,
          |  tournament_id text not null,
          |  stage_id text not null,
          |  table_no integer not null,
          |  status text not null,
          |  payload jsonb not null,
          |  updated_at timestamptz not null default now()
          |)
          |""".stripMargin
      )
      execute(connection, "alter table tables add column if not exists tournament_id text")
      execute(connection, "alter table tables add column if not exists stage_id text")
      execute(connection, "alter table tables add column if not exists table_no integer")
      execute(connection, "alter table tables add column if not exists status text")
      execute(connection, "alter table tables add column if not exists payload jsonb")
      execute(connection, "alter table tables add column if not exists updated_at timestamptz default now()")
      execute(
        connection,
        "create unique index if not exists idx_tables_stage_table_no on tables (tournament_id, stage_id, table_no)"
      )
      execute(
        connection,
        "create index if not exists idx_tables_tournament_stage on tables (tournament_id, stage_id)"
      )

      execute(
        connection,
        """
          |create table if not exists paifus (
          |  id text primary key,
          |  table_id text not null,
          |  tournament_id text not null,
          |  stage_id text not null,
          |  recorded_at timestamptz not null,
          |  player_ids text[] not null,
          |  payload jsonb not null,
          |  updated_at timestamptz not null default now()
          |)
          |""".stripMargin
      )
      execute(connection, "alter table paifus add column if not exists table_id text")
      execute(connection, "alter table paifus add column if not exists tournament_id text")
      execute(connection, "alter table paifus add column if not exists stage_id text")
      execute(connection, "alter table paifus add column if not exists recorded_at timestamptz")
      execute(connection, "alter table paifus add column if not exists player_ids text[]")
      execute(connection, "alter table paifus add column if not exists payload jsonb")
      execute(connection, "alter table paifus add column if not exists updated_at timestamptz default now()")
      execute(connection, "create index if not exists idx_paifus_table_id on paifus (table_id)")
      execute(connection, "create index if not exists idx_paifus_recorded_at on paifus (recorded_at)")
      execute(connection, "create index if not exists idx_paifus_player_ids on paifus using gin (player_ids)")

      execute(
        connection,
        """
          |create table if not exists match_records (
          |  id text primary key,
          |  table_id text not null,
          |  tournament_id text not null,
          |  stage_id text not null,
          |  generated_at timestamptz not null,
          |  player_ids text[] not null,
          |  payload jsonb not null,
          |  updated_at timestamptz not null default now()
          |)
          |""".stripMargin
      )
      execute(connection, "alter table match_records add column if not exists table_id text")
      execute(connection, "alter table match_records add column if not exists tournament_id text")
      execute(connection, "alter table match_records add column if not exists stage_id text")
      execute(connection, "alter table match_records add column if not exists generated_at timestamptz")
      execute(connection, "alter table match_records add column if not exists player_ids text[]")
      execute(connection, "alter table match_records add column if not exists payload jsonb")
      execute(connection, "alter table match_records add column if not exists updated_at timestamptz default now()")
      execute(connection, "create unique index if not exists idx_match_records_table_id on match_records (table_id)")
      execute(connection, "create index if not exists idx_match_records_tournament_id on match_records (tournament_id)")
      execute(connection, "create index if not exists idx_match_records_player_ids on match_records using gin (player_ids)")

      execute(
        connection,
        """
          |create table if not exists appeal_tickets (
          |  id text primary key,
          |  table_id text not null,
          |  tournament_id text not null,
          |  stage_id text not null,
          |  status text not null,
          |  opened_by text not null,
          |  payload jsonb not null,
          |  updated_at timestamptz not null default now()
          |)
          |""".stripMargin
      )
      execute(connection, "alter table appeal_tickets add column if not exists table_id text")
      execute(connection, "alter table appeal_tickets add column if not exists tournament_id text")
      execute(connection, "alter table appeal_tickets add column if not exists stage_id text")
      execute(connection, "alter table appeal_tickets add column if not exists status text")
      execute(connection, "alter table appeal_tickets add column if not exists opened_by text")
      execute(connection, "alter table appeal_tickets add column if not exists payload jsonb")
      execute(connection, "alter table appeal_tickets add column if not exists updated_at timestamptz default now()")
      execute(connection, "create index if not exists idx_appeals_tournament_id on appeal_tickets (tournament_id)")
      execute(connection, "create index if not exists idx_appeals_table_id on appeal_tickets (table_id)")

      execute(
        connection,
        """
          |create table if not exists guest_sessions (
          |  id text primary key,
          |  created_at timestamptz not null,
          |  display_name text not null,
          |  payload jsonb not null,
          |  updated_at timestamptz not null default now()
          |)
          |""".stripMargin
      )
      execute(connection, "alter table guest_sessions add column if not exists created_at timestamptz")
      execute(connection, "alter table guest_sessions add column if not exists display_name text")
      execute(connection, "alter table guest_sessions add column if not exists payload jsonb")
      execute(connection, "alter table guest_sessions add column if not exists updated_at timestamptz default now()")
      execute(connection, "create index if not exists idx_guest_sessions_created_at on guest_sessions (created_at desc)")

      execute(
        connection,
        """
          |create table if not exists account_credentials (
          |  username text primary key,
          |  player_id text not null,
          |  payload jsonb not null,
          |  updated_at timestamptz not null default now()
          |)
          |""".stripMargin
      )
      execute(connection, "alter table account_credentials add column if not exists player_id text")
      execute(connection, "alter table account_credentials add column if not exists payload jsonb")
      execute(connection, "alter table account_credentials add column if not exists updated_at timestamptz default now()")
      execute(connection, "create unique index if not exists idx_account_credentials_player_id on account_credentials (player_id)")

      execute(
        connection,
        """
          |create table if not exists authenticated_sessions (
          |  token text primary key,
          |  username text not null,
          |  player_id text not null,
          |  created_at timestamptz not null,
          |  expires_at timestamptz not null,
          |  payload jsonb not null,
          |  updated_at timestamptz not null default now()
          |)
          |""".stripMargin
      )
      execute(connection, "alter table authenticated_sessions add column if not exists username text")
      execute(connection, "alter table authenticated_sessions add column if not exists player_id text")
      execute(connection, "alter table authenticated_sessions add column if not exists created_at timestamptz")
      execute(connection, "alter table authenticated_sessions add column if not exists expires_at timestamptz")
      execute(connection, "alter table authenticated_sessions add column if not exists payload jsonb")
      execute(connection, "alter table authenticated_sessions add column if not exists updated_at timestamptz default now()")
      execute(connection, "create index if not exists idx_authenticated_sessions_player_id on authenticated_sessions (player_id)")
      execute(connection, "create index if not exists idx_authenticated_sessions_username on authenticated_sessions (username)")
      execute(connection, "create index if not exists idx_authenticated_sessions_expires_at on authenticated_sessions (expires_at)")

      execute(
        connection,
        """
          |create table if not exists dashboards (
          |  owner_key text primary key,
          |  owner_type text not null,
          |  payload jsonb not null,
          |  updated_at timestamptz not null default now()
          |)
          |""".stripMargin
      )
      execute(connection, "alter table dashboards add column if not exists owner_type text")
      execute(connection, "alter table dashboards add column if not exists payload jsonb")
      execute(connection, "alter table dashboards add column if not exists updated_at timestamptz default now()")
      execute(
        connection,
        """
          |create table if not exists advanced_stats_boards (
          |  owner_key text primary key,
          |  owner_type text not null,
          |  payload jsonb not null,
          |  updated_at timestamptz not null default now()
          |)
          |""".stripMargin
      )
      execute(connection, "alter table advanced_stats_boards add column if not exists owner_type text")
      execute(connection, "alter table advanced_stats_boards add column if not exists payload jsonb")
      execute(connection, "alter table advanced_stats_boards add column if not exists updated_at timestamptz default now()")
      execute(
        connection,
        """
          |create table if not exists advanced_stats_recompute_tasks (
          |  id text primary key,
          |  owner_key text not null,
          |  owner_type text not null,
          |  status text not null,
          |  calculator_version integer not null,
          |  requested_at timestamptz not null,
          |  payload jsonb not null,
          |  updated_at timestamptz not null default now()
          |)
          |""".stripMargin
      )
      execute(connection, "alter table advanced_stats_recompute_tasks add column if not exists owner_key text")
      execute(connection, "alter table advanced_stats_recompute_tasks add column if not exists owner_type text")
      execute(connection, "alter table advanced_stats_recompute_tasks add column if not exists status text")
      execute(connection, "alter table advanced_stats_recompute_tasks add column if not exists calculator_version integer")
      execute(connection, "alter table advanced_stats_recompute_tasks add column if not exists requested_at timestamptz")
      execute(connection, "alter table advanced_stats_recompute_tasks add column if not exists payload jsonb")
      execute(connection, "alter table advanced_stats_recompute_tasks add column if not exists updated_at timestamptz default now()")
      execute(
        connection,
        "create index if not exists idx_advanced_stats_tasks_pending on advanced_stats_recompute_tasks (status, requested_at)"
      )
      execute(
        connection,
        "create index if not exists idx_advanced_stats_tasks_owner on advanced_stats_recompute_tasks (owner_key, calculator_version, status)"
      )
      execute(
        connection,
        """
          |create table if not exists global_dictionary (
          |  key text primary key,
          |  updated_at timestamptz not null,
          |  payload jsonb not null
          |)
          |""".stripMargin
      )
      execute(connection, "alter table global_dictionary add column if not exists updated_at timestamptz")
      execute(connection, "alter table global_dictionary add column if not exists payload jsonb")
      execute(
        connection,
        """
          |create table if not exists tournament_settlements (
          |  id text primary key,
          |  tournament_id text not null,
          |  stage_id text not null,
          |  generated_at timestamptz not null,
          |  payload jsonb not null,
          |  updated_at timestamptz not null default now()
          |)
          |""".stripMargin
      )
      execute(connection, "alter table tournament_settlements add column if not exists tournament_id text")
      execute(connection, "alter table tournament_settlements add column if not exists stage_id text")
      execute(connection, "alter table tournament_settlements add column if not exists generated_at timestamptz")
      execute(connection, "alter table tournament_settlements add column if not exists payload jsonb")
      execute(connection, "alter table tournament_settlements add column if not exists updated_at timestamptz default now()")
      execute(connection, "create unique index if not exists idx_tournament_settlements_scope on tournament_settlements (tournament_id, stage_id)")
      execute(connection, "create index if not exists idx_tournament_settlements_generated_at on tournament_settlements (generated_at)")
      execute(
        connection,
        """
          |create table if not exists event_cascade_records (
          |  id text primary key,
          |  consumer text not null,
          |  status text not null,
          |  aggregate_type text not null,
          |  aggregate_id text not null,
          |  occurred_at timestamptz not null,
          |  payload jsonb not null,
          |  updated_at timestamptz not null default now()
          |)
          |""".stripMargin
      )
      execute(connection, "alter table event_cascade_records add column if not exists consumer text")
      execute(connection, "alter table event_cascade_records add column if not exists status text")
      execute(connection, "alter table event_cascade_records add column if not exists aggregate_type text")
      execute(connection, "alter table event_cascade_records add column if not exists aggregate_id text")
      execute(connection, "alter table event_cascade_records add column if not exists occurred_at timestamptz")
      execute(connection, "alter table event_cascade_records add column if not exists payload jsonb")
      execute(connection, "alter table event_cascade_records add column if not exists updated_at timestamptz default now()")
      execute(connection, "create index if not exists idx_event_cascade_records_status on event_cascade_records (status, occurred_at)")
      execute(connection, "create index if not exists idx_event_cascade_records_consumer on event_cascade_records (consumer, occurred_at)")
      execute(connection, "create index if not exists idx_event_cascade_records_aggregate on event_cascade_records (aggregate_type, aggregate_id)")
      execute(
        connection,
        """
          |create table if not exists domain_event_outbox (
          |  id text primary key,
          |  sequence_no bigint not null,
          |  event_type text not null,
          |  aggregate_type text not null,
          |  aggregate_id text not null,
          |  status text not null,
          |  occurred_at timestamptz not null,
          |  available_at timestamptz not null,
          |  payload jsonb not null,
          |  updated_at timestamptz not null default now()
          |)
          |""".stripMargin
      )
      execute(connection, "create sequence if not exists domain_event_outbox_sequence start 1")
      execute(connection, "alter table domain_event_outbox add column if not exists sequence_no bigint")
      execute(connection, "alter table domain_event_outbox add column if not exists event_type text")
      execute(connection, "alter table domain_event_outbox add column if not exists aggregate_type text")
      execute(connection, "alter table domain_event_outbox add column if not exists aggregate_id text")
      execute(connection, "alter table domain_event_outbox add column if not exists status text")
      execute(connection, "alter table domain_event_outbox add column if not exists occurred_at timestamptz")
      execute(connection, "alter table domain_event_outbox add column if not exists available_at timestamptz")
      execute(connection, "alter table domain_event_outbox add column if not exists payload jsonb")
      execute(connection, "alter table domain_event_outbox add column if not exists updated_at timestamptz default now()")
      execute(
        connection,
        """
          |update domain_event_outbox
          |set sequence_no = numbered.sequence_no
          |from (
          |  select id, row_number() over(order by occurred_at asc, id asc) as sequence_no
          |  from domain_event_outbox
          |) as numbered
          |where domain_event_outbox.id = numbered.id
          |  and domain_event_outbox.sequence_no is null
          |""".stripMargin
      )
      execute(connection, "update domain_event_outbox set aggregate_type = coalesce(aggregate_type, 'domain-event')")
      execute(connection, "update domain_event_outbox set aggregate_id = coalesce(aggregate_id, id)")
      execute(
        connection,
        """
          |update domain_event_outbox
          |set payload =
          |  jsonb_set(
          |    jsonb_set(
          |      jsonb_set(payload, '{sequenceNo}', to_jsonb(sequence_no), true),
          |      '{aggregateType}',
          |      to_jsonb(aggregate_type),
          |      true
          |    ),
          |    '{aggregateId}',
          |    to_jsonb(aggregate_id),
          |    true
          |  )
          |where not (payload ? 'sequenceNo')
          |   or not (payload ? 'aggregateType')
          |   or not (payload ? 'aggregateId')
          |""".stripMargin
      )
      execute(connection, "create unique index if not exists idx_domain_event_outbox_sequence on domain_event_outbox (sequence_no)")
      execute(connection, "create index if not exists idx_domain_event_outbox_status on domain_event_outbox (status, available_at, sequence_no)")
      execute(connection, "create index if not exists idx_domain_event_outbox_occurred_at on domain_event_outbox (occurred_at)")
      execute(connection, "create index if not exists idx_domain_event_outbox_aggregate on domain_event_outbox (aggregate_type, aggregate_id, sequence_no)")
      execute(
        connection,
        """
          |select case
          |  when exists(select 1 from domain_event_outbox)
          |    then setval('domain_event_outbox_sequence', (select max(sequence_no) from domain_event_outbox), true)
          |  else setval('domain_event_outbox_sequence', 1, false)
          |end
          |""".stripMargin
      )
      execute(
        connection,
        """
          |create table if not exists domain_event_delivery_receipts (
          |  id text primary key,
          |  outbox_record_id text not null,
          |  subscriber_id text not null,
          |  event_type text not null,
          |  delivered_at timestamptz not null,
          |  payload jsonb not null,
          |  updated_at timestamptz not null default now()
          |)
          |""".stripMargin
      )
      execute(connection, "alter table domain_event_delivery_receipts add column if not exists outbox_record_id text")
      execute(connection, "alter table domain_event_delivery_receipts add column if not exists subscriber_id text")
      execute(connection, "alter table domain_event_delivery_receipts add column if not exists event_type text")
      execute(connection, "alter table domain_event_delivery_receipts add column if not exists delivered_at timestamptz")
      execute(connection, "alter table domain_event_delivery_receipts add column if not exists payload jsonb")
      execute(connection, "alter table domain_event_delivery_receipts add column if not exists updated_at timestamptz default now()")
      execute(connection, "create unique index if not exists idx_domain_event_delivery_receipts_unique on domain_event_delivery_receipts (outbox_record_id, subscriber_id)")
      execute(connection, "create index if not exists idx_domain_event_delivery_receipts_outbox on domain_event_delivery_receipts (outbox_record_id, delivered_at)")
      execute(
        connection,
        """
          |create table if not exists domain_event_subscriber_cursors (
          |  id text primary key,
          |  subscriber_id text not null,
          |  partition_key text not null,
          |  last_outbox_record_id text not null,
          |  last_sequence_no bigint not null,
          |  advanced_at timestamptz not null,
          |  payload jsonb not null,
          |  updated_at timestamptz not null default now()
          |)
          |""".stripMargin
      )
      execute(connection, "alter table domain_event_subscriber_cursors add column if not exists subscriber_id text")
      execute(connection, "alter table domain_event_subscriber_cursors add column if not exists partition_key text")
      execute(connection, "alter table domain_event_subscriber_cursors add column if not exists last_outbox_record_id text")
      execute(connection, "alter table domain_event_subscriber_cursors add column if not exists last_sequence_no bigint")
      execute(connection, "alter table domain_event_subscriber_cursors add column if not exists advanced_at timestamptz")
      execute(connection, "alter table domain_event_subscriber_cursors add column if not exists payload jsonb")
      execute(connection, "alter table domain_event_subscriber_cursors add column if not exists updated_at timestamptz default now()")
      execute(connection, "create unique index if not exists idx_domain_event_subscriber_cursors_unique on domain_event_subscriber_cursors (subscriber_id, partition_key)")
      execute(connection, "create index if not exists idx_domain_event_subscriber_cursors_advanced_at on domain_event_subscriber_cursors (advanced_at)")
      execute(
        connection,
        """
          |create table if not exists audit_events (
          |  id text primary key,
          |  aggregate_type text not null,
          |  aggregate_id text not null,
          |  event_type text not null,
          |  occurred_at timestamptz not null,
          |  actor_id text null,
          |  payload jsonb not null
          |)
          |""".stripMargin
      )
      execute(connection, "alter table audit_events add column if not exists aggregate_type text")
      execute(connection, "alter table audit_events add column if not exists aggregate_id text")
      execute(connection, "alter table audit_events add column if not exists event_type text")
      execute(connection, "alter table audit_events add column if not exists occurred_at timestamptz")
      execute(connection, "alter table audit_events add column if not exists actor_id text")
      execute(connection, "alter table audit_events add column if not exists payload jsonb")
      execute(connection, "create index if not exists idx_audit_events_aggregate on audit_events (aggregate_type, aggregate_id)")
      execute(connection, "create index if not exists idx_audit_events_occurred_at on audit_events (occurred_at)")
      execute(
        connection,
        """
          |insert into schema_version(version, description)
          |values (1, 'Initial RiichiNexus PostgreSQL schema')
          |on conflict (version) do nothing
          |""".stripMargin
      )
      execute(
        connection,
        """
          |insert into schema_version(version, description)
          |values (2, 'Extended RiichiNexus competition workflow schema')
          |on conflict (version) do nothing
          |""".stripMargin
      )
      execute(
        connection,
        """
          |insert into schema_version(version, description)
          |values (3, 'Added settlement persistence and audit event schema')
          |on conflict (version) do nothing
          |""".stripMargin
      )
      execute(
        connection,
        """
          |insert into schema_version(version, description)
          |values (4, 'Added guest session persistence schema')
          |on conflict (version) do nothing
          |""".stripMargin
      )
      execute(
        connection,
        """
          |insert into schema_version(version, description)
          |values (5, 'Added advanced stats board persistence schema')
          |on conflict (version) do nothing
          |""".stripMargin
      )
      execute(
        connection,
        """
          |insert into schema_version(version, description)
          |values (6, 'Added advanced stats recompute task pipeline schema')
          |on conflict (version) do nothing
          |""".stripMargin
      )
      execute(
        connection,
        """
          |insert into schema_version(version, description)
          |values (7, 'Added event cascade record subscriber schema')
          |on conflict (version) do nothing
          |""".stripMargin
      )
      execute(
        connection,
        """
          |insert into schema_version(version, description)
          |values (8, 'Added dictionary namespace governance schema')
          |on conflict (version) do nothing
          |""".stripMargin
      )
      execute(
        connection,
        """
          |insert into schema_version(version, description)
          |values (9, 'Added durable domain event outbox schema')
          |on conflict (version) do nothing
          |""".stripMargin
      )
      execute(
        connection,
        """
          |insert into schema_version(version, description)
          |values (10, 'Added domain event delivery receipt schema')
          |on conflict (version) do nothing
          |""".stripMargin
      )
      execute(
        connection,
        """
          |insert into schema_version(version, description)
          |values (11, 'Added subscriber ordering cursor and partitioned outbox schema')
          |on conflict (version) do nothing
          |""".stripMargin
      )
      execute(
        connection,
        """
          |insert into schema_version(version, description)
          |values (12, 'Added account credential and authenticated session schema')
          |on conflict (version) do nothing
          |""".stripMargin
      )
    }

  private def execute(connection: Connection, sql: String): Unit =
    Using.resource(connection.createStatement()) { statement =>
      statement.execute(sql)
    }

object PostgresSchemaInitializer:
  def apply(connectionFactory: JdbcConnectionFactory): PostgresSchemaInitializer =
    new PostgresSchemaInitializer(connectionFactory)

final class PostgresAdminService(connectionFactory: JdbcConnectionFactory):
  private val managedTables =
    Vector(
      "players",
      "account_credentials",
      "authenticated_sessions",
      "guest_sessions",
      "clubs",
      "tournaments",
      "tables",
      "match_records",
      "paifus",
      "appeal_tickets",
      "dashboards",
      "advanced_stats_boards",
      "advanced_stats_recompute_tasks",
      "global_dictionary",
      "dictionary_namespaces",
      "tournament_settlements",
      "event_cascade_records",
      "domain_event_outbox",
      "domain_event_delivery_receipts",
      "domain_event_subscriber_cursors",
      "audit_events"
    )

  def ping(): Boolean =
    connectionFactory.withConnection { connection =>
      Using.resource(connection.prepareStatement("select 1")) { statement =>
        Using.resource(statement.executeQuery())(_.next())
      }
    }

  def schemaVersion(): Option[Int] =
    connectionFactory.withConnection { connection =>
      Using.resource(connection.prepareStatement("select max(version) as version from schema_version")) {
        statement =>
          Using.resource(statement.executeQuery()) { resultSet =>
            if resultSet.next() then Option(resultSet.getObject("version")).map(_ => resultSet.getInt("version"))
            else None
          }
      }
    }

  def tableCounts(): Vector[(String, Int)] =
    managedTables.map { tableName =>
      tableName -> countRows(tableName)
    }

  def truncateAll(): Unit =
    connectionFactory.inTransaction {
      connectionFactory.withConnection { connection =>
        executeAdmin(connection, "truncate table audit_events restart identity")
        executeAdmin(connection, "truncate table domain_event_subscriber_cursors restart identity")
        executeAdmin(connection, "truncate table domain_event_delivery_receipts restart identity")
        executeAdmin(connection, "truncate table domain_event_outbox restart identity")
        executeAdmin(connection, "select setval('domain_event_outbox_sequence', 1, false)")
        executeAdmin(connection, "truncate table tournament_settlements restart identity")
        executeAdmin(connection, "truncate table event_cascade_records restart identity")
        executeAdmin(connection, "truncate table global_dictionary restart identity")
        executeAdmin(connection, "truncate table dictionary_namespaces restart identity")
        executeAdmin(connection, "truncate table advanced_stats_recompute_tasks restart identity")
        executeAdmin(connection, "truncate table dashboards restart identity")
        executeAdmin(connection, "truncate table advanced_stats_boards restart identity")
        executeAdmin(connection, "truncate table authenticated_sessions restart identity")
        executeAdmin(connection, "truncate table account_credentials restart identity")
        executeAdmin(connection, "truncate table guest_sessions restart identity")
        executeAdmin(connection, "truncate table appeal_tickets restart identity")
        executeAdmin(connection, "truncate table paifus restart identity")
        executeAdmin(connection, "truncate table match_records restart identity")
        executeAdmin(connection, "truncate table tables restart identity")
        executeAdmin(connection, "truncate table tournaments restart identity")
        executeAdmin(connection, "truncate table clubs restart identity")
        executeAdmin(connection, "truncate table players restart identity")
      }
    }

  private def executeAdmin(connection: Connection, sql: String): Unit =
    Using.resource(connection.createStatement())(_.execute(sql))

  private def countRows(tableName: String): Int =
    connectionFactory.withConnection { connection =>
      Using.resource(connection.prepareStatement(s"select count(*) as row_count from $tableName")) { statement =>
        Using.resource(statement.executeQuery()) { resultSet =>
          if resultSet.next() then resultSet.getInt("row_count") else 0
        }
      }
    }

object PostgresAdminService:
  def apply(connectionFactory: JdbcConnectionFactory): PostgresAdminService =
    new PostgresAdminService(connectionFactory)
