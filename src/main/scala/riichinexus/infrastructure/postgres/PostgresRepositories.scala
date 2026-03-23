package riichinexus.infrastructure.postgres

import java.sql.Connection
import java.sql.DriverManager
import java.sql.PreparedStatement
import java.sql.SQLException
import java.sql.Timestamp
import java.sql.Types

import scala.util.Using

import org.postgresql.util.PSQLException

import riichinexus.application.ports.*
import riichinexus.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import upickle.default.*

private object PostgresErrors:
  private val UniqueViolation = "23505"

  def isUniqueViolation(error: SQLException, constraint: String): Boolean =
    Option(error.getSQLState).contains(UniqueViolation) &&
      constraintName(error).contains(constraint)

  private def constraintName(error: SQLException): Option[String] =
    error match
      case postgresError: PSQLException =>
        Option(postgresError.getServerErrorMessage).map(_.getConstraint)
      case _ =>
        None

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
    }

  private def execute(connection: Connection, sql: String): Unit =
    Using.resource(connection.createStatement()) { statement =>
      statement.execute(sql)
    }

private trait JdbcRepositorySupport:
  protected val connectionFactory: JdbcConnectionFactory

  protected def withConnection[A](f: Connection => A): A =
    connectionFactory.withConnection(f)

  protected def setNullableString(
      statement: PreparedStatement,
      index: Int,
      value: Option[String]
  ): Unit =
    value match
      case Some(actual) => statement.setString(index, actual)
      case None         => statement.setNull(index, Types.VARCHAR)

  protected def readOne[T: Reader](
      sql: String,
      bind: PreparedStatement => Unit
  ): Option[T] =
    withConnection { connection =>
      Using.resource(connection.prepareStatement(sql)) { statement =>
        bind(statement)
        Using.resource(statement.executeQuery()) { resultSet =>
          if resultSet.next() then Some(read[T](resultSet.getString("payload")))
          else None
        }
      }
    }

  protected def readAll[T: Reader](
      sql: String,
      bind: PreparedStatement => Unit = _ => ()
  ): Vector[T] =
    withConnection { connection =>
      Using.resource(connection.prepareStatement(sql)) { statement =>
        bind(statement)
        Using.resource(statement.executeQuery()) { resultSet =>
          val buffer = Vector.newBuilder[T]
          while resultSet.next() do
            buffer += read[T](resultSet.getString("payload"))
          buffer.result()
        }
      }
    }

  protected def writeJson[T: Writer](value: T): String =
    write(value)

  protected def payloadVersionSql(payloadColumn: String = "payload"): String =
    s"cast($payloadColumn ->> 'version' as integer)"

  protected def requireOptimisticUpdate(
      rowsUpdated: Int,
      aggregateType: String,
      aggregateId: String,
      expectedVersion: Int,
      actualVersion: => Option[Int]
  ): Unit =
    if rowsUpdated == 0 then
      throw OptimisticConcurrencyException(
        aggregateType = aggregateType,
        aggregateId = aggregateId,
        expectedVersion = expectedVersion,
        actualVersion = actualVersion
      )

final class PostgresPlayerRepository(
    protected val connectionFactory: JdbcConnectionFactory
) extends PlayerRepository
    with JdbcRepositorySupport:
  override def save(player: Player): Player =
    try persist(player)
    catch
      case error: SQLException if PostgresErrors.isUniqueViolation(error, "idx_players_user_id") =>
        val normalized = findByUserId(player.userId)
          .map(existing => player.copy(id = existing.id, registeredAt = existing.registeredAt, version = existing.version))
          .getOrElse(throw error)
        persist(normalized)

  private def persist(player: Player): Player =
    val persisted = player.copy(version = player.version + 1)
    val sql =
      """
        |insert into players (id, user_id, nickname, club_id, elo, payload, updated_at)
        |values (?, ?, ?, ?, ?, cast(? as jsonb), now())
        |on conflict (id) do update set
        |  user_id = excluded.user_id,
        |  nickname = excluded.nickname,
        |  club_id = excluded.club_id,
        |  elo = excluded.elo,
        |  payload = excluded.payload,
        |  updated_at = now()
        |where cast(players.payload ->> 'version' as integer) = ?
        |""".stripMargin

    val rowsUpdated = withConnection { connection =>
      Using.resource(connection.prepareStatement(sql)) { statement =>
        statement.setString(1, persisted.id.value)
        statement.setString(2, persisted.userId)
        statement.setString(3, persisted.nickname)
        setNullableString(statement, 4, persisted.clubId.map(_.value))
        statement.setInt(5, persisted.elo)
        statement.setString(6, writeJson(persisted))
        statement.setInt(7, player.version)
        statement.executeUpdate()
      }
    }

    requireOptimisticUpdate(rowsUpdated, "player", persisted.id.value, player.version, findById(persisted.id).map(_.version))
    persisted

  override def findById(id: PlayerId): Option[Player] =
    readOne[Player]("select payload from players where id = ?", { statement =>
      statement.setString(1, id.value)
    })

  override def findByUserId(userId: String): Option[Player] =
    readOne[Player]("select payload from players where user_id = ?", { statement =>
      statement.setString(1, userId)
    })

  override def findAll(): Vector[Player] =
    readAll[Player]("select payload from players order by nickname")

final class PostgresGuestSessionRepository(
    protected val connectionFactory: JdbcConnectionFactory
) extends GuestSessionRepository
    with JdbcRepositorySupport:
  override def save(session: GuestAccessSession): GuestAccessSession =
    val persisted = session.copy(version = session.version + 1)
    val sql =
      """
        |insert into guest_sessions (id, created_at, display_name, payload, updated_at)
        |values (?, ?, ?, cast(? as jsonb), now())
        |on conflict (id) do update set
        |  created_at = excluded.created_at,
        |  display_name = excluded.display_name,
        |  payload = excluded.payload,
        |  updated_at = now()
        |where cast(guest_sessions.payload ->> 'version' as integer) = ?
        |""".stripMargin

    val rowsUpdated = withConnection { connection =>
      Using.resource(connection.prepareStatement(sql)) { statement =>
        statement.setString(1, persisted.id.value)
        statement.setTimestamp(2, Timestamp.from(persisted.createdAt))
        statement.setString(3, persisted.displayName)
        statement.setString(4, writeJson(persisted))
        statement.setInt(5, session.version)
        statement.executeUpdate()
      }
    }

    requireOptimisticUpdate(rowsUpdated, "guest-session", persisted.id.value, session.version, findById(persisted.id).map(_.version))
    persisted

  override def findById(id: GuestSessionId): Option[GuestAccessSession] =
    readOne[GuestAccessSession]("select payload from guest_sessions where id = ?", { statement =>
      statement.setString(1, id.value)
    })

  override def findAll(): Vector[GuestAccessSession] =
    readAll[GuestAccessSession]("select payload from guest_sessions order by created_at desc")

final class PostgresClubRepository(
    protected val connectionFactory: JdbcConnectionFactory
) extends ClubRepository
    with JdbcRepositorySupport:
  override def save(club: Club): Club =
    try persist(club)
    catch
      case error: SQLException if PostgresErrors.isUniqueViolation(error, "idx_clubs_name") =>
        val normalized = findByName(club.name)
          .map(existing => club.copy(id = existing.id, creator = existing.creator, createdAt = existing.createdAt, version = existing.version))
          .getOrElse(throw error)
        persist(normalized)

  private def persist(club: Club): Club =
    val persisted = club.copy(version = club.version + 1)
    val sql =
      """
        |insert into clubs (id, name, creator_id, total_points, payload, updated_at)
        |values (?, ?, ?, ?, cast(? as jsonb), now())
        |on conflict (id) do update set
        |  name = excluded.name,
        |  creator_id = excluded.creator_id,
        |  total_points = excluded.total_points,
        |  payload = excluded.payload,
        |  updated_at = now()
        |where cast(clubs.payload ->> 'version' as integer) = ?
        |""".stripMargin

    val rowsUpdated = withConnection { connection =>
      Using.resource(connection.prepareStatement(sql)) { statement =>
        statement.setString(1, persisted.id.value)
        statement.setString(2, persisted.name)
        statement.setString(3, persisted.creator.value)
        statement.setInt(4, persisted.totalPoints)
        statement.setString(5, writeJson(persisted))
        statement.setInt(6, club.version)
        statement.executeUpdate()
      }
    }

    requireOptimisticUpdate(rowsUpdated, "club", persisted.id.value, club.version, findById(persisted.id).map(_.version))
    persisted

  override def findById(id: ClubId): Option[Club] =
    readOne[Club]("select payload from clubs where id = ?", { statement =>
      statement.setString(1, id.value)
    })

  override def findByName(name: String): Option[Club] =
    readOne[Club]("select payload from clubs where name = ?", { statement =>
      statement.setString(1, name)
    })

  override def findAll(): Vector[Club] =
    readAll[Club]("select payload from clubs order by name")

final class PostgresTournamentRepository(
    protected val connectionFactory: JdbcConnectionFactory
) extends TournamentRepository
    with JdbcRepositorySupport:
  override def save(tournament: Tournament): Tournament =
    try persist(tournament)
    catch
      case error: SQLException if PostgresErrors.isUniqueViolation(error, "idx_tournaments_name_start") =>
        val normalized = findByNameAndOrganizer(tournament.name, tournament.organizer)
          .map(existing => tournament.copy(id = existing.id, version = existing.version))
          .getOrElse(throw error)
        persist(normalized)

  private def persist(tournament: Tournament): Tournament =
    val persisted = tournament.copy(version = tournament.version + 1)
    val sql =
      """
        |insert into tournaments (id, name, organizer, status, payload, updated_at)
        |values (?, ?, ?, ?, cast(? as jsonb), now())
        |on conflict (id) do update set
        |  name = excluded.name,
        |  organizer = excluded.organizer,
        |  status = excluded.status,
        |  payload = excluded.payload,
        |  updated_at = now()
        |where cast(tournaments.payload ->> 'version' as integer) = ?
        |""".stripMargin

    val rowsUpdated = withConnection { connection =>
      Using.resource(connection.prepareStatement(sql)) { statement =>
        statement.setString(1, persisted.id.value)
        statement.setString(2, persisted.name)
        statement.setString(3, persisted.organizer)
        statement.setString(4, persisted.status.toString)
        statement.setString(5, writeJson(persisted))
        statement.setInt(6, tournament.version)
        statement.executeUpdate()
      }
    }

    requireOptimisticUpdate(rowsUpdated, "tournament", persisted.id.value, tournament.version, findById(persisted.id).map(_.version))
    persisted

  override def findById(id: TournamentId): Option[Tournament] =
    readOne[Tournament]("select payload from tournaments where id = ?", { statement =>
      statement.setString(1, id.value)
    })

  override def findByNameAndOrganizer(name: String, organizer: String): Option[Tournament] =
    readOne[Tournament](
      "select payload from tournaments where name = ? and organizer = ?",
      { statement =>
        statement.setString(1, name)
        statement.setString(2, organizer)
      }
    )

  override def findAll(): Vector[Tournament] =
    readAll[Tournament]("select payload from tournaments order by updated_at desc")

final class PostgresTableRepository(
    protected val connectionFactory: JdbcConnectionFactory
) extends TableRepository
    with JdbcRepositorySupport:
  override def save(table: Table): Table =
    val persisted = table.copy(version = table.version + 1)
    val sql =
      """
        |insert into tables (id, tournament_id, stage_id, table_no, status, payload, updated_at)
        |values (?, ?, ?, ?, ?, cast(? as jsonb), now())
        |on conflict (id) do update set
        |  tournament_id = excluded.tournament_id,
        |  stage_id = excluded.stage_id,
        |  table_no = excluded.table_no,
        |  status = excluded.status,
        |  payload = excluded.payload,
        |  updated_at = now()
        |where cast(tables.payload ->> 'version' as integer) = ?
        |""".stripMargin

    val rowsUpdated = withConnection { connection =>
      Using.resource(connection.prepareStatement(sql)) { statement =>
        statement.setString(1, persisted.id.value)
        statement.setString(2, persisted.tournamentId.value)
        statement.setString(3, persisted.stageId.value)
        statement.setInt(4, persisted.tableNo)
        statement.setString(5, persisted.status.toString)
        statement.setString(6, writeJson(persisted))
        statement.setInt(7, table.version)
        statement.executeUpdate()
      }
    }

    requireOptimisticUpdate(rowsUpdated, "table", persisted.id.value, table.version, findById(persisted.id).map(_.version))
    persisted

  override def delete(id: TableId): Unit =
    withConnection { connection =>
      Using.resource(connection.prepareStatement("delete from tables where id = ?")) { statement =>
        statement.setString(1, id.value)
        statement.executeUpdate()
      }
    }

  override def findById(id: TableId): Option[Table] =
    readOne[Table]("select payload from tables where id = ?", { statement =>
      statement.setString(1, id.value)
    })

  override def findByTournamentAndStage(
      tournamentId: TournamentId,
      stageId: TournamentStageId
  ): Vector[Table] =
    readAll[Table](
      "select payload from tables where tournament_id = ? and stage_id = ? order by table_no",
      { statement =>
        statement.setString(1, tournamentId.value)
        statement.setString(2, stageId.value)
      }
    )

  override def findAll(): Vector[Table] =
    readAll[Table]("select payload from tables order by updated_at desc")

final class PostgresMatchRecordRepository(
    protected val connectionFactory: JdbcConnectionFactory
) extends MatchRecordRepository
    with JdbcRepositorySupport:
  override def save(record: MatchRecord): MatchRecord =
    val sql =
      """
        |insert into match_records (id, table_id, tournament_id, stage_id, generated_at, player_ids, payload, updated_at)
        |values (?, ?, ?, ?, ?, ?, cast(? as jsonb), now())
        |on conflict (id) do update set
        |  table_id = excluded.table_id,
        |  tournament_id = excluded.tournament_id,
        |  stage_id = excluded.stage_id,
        |  generated_at = excluded.generated_at,
        |  player_ids = excluded.player_ids,
        |  payload = excluded.payload,
        |  updated_at = now()
        |""".stripMargin

    withConnection { connection =>
      Using.resource(connection.prepareStatement(sql)) { statement =>
        statement.setString(1, record.id.value)
        statement.setString(2, record.tableId.value)
        statement.setString(3, record.tournamentId.value)
        statement.setString(4, record.stageId.value)
        statement.setTimestamp(5, Timestamp.from(record.generatedAt))
        statement.setArray(
          6,
          connection.createArrayOf("text", record.playerIds.map(_.value).toArray)
        )
        statement.setString(7, writeJson(record))
        statement.executeUpdate()
      }
    }

    record

  override def findById(id: MatchRecordId): Option[MatchRecord] =
    readOne[MatchRecord]("select payload from match_records where id = ?", { statement =>
      statement.setString(1, id.value)
    })

  override def findByTable(tableId: TableId): Option[MatchRecord] =
    readOne[MatchRecord]("select payload from match_records where table_id = ?", { statement =>
      statement.setString(1, tableId.value)
    })

  override def findAll(): Vector[MatchRecord] =
    readAll[MatchRecord]("select payload from match_records order by generated_at desc")

final class PostgresPaifuRepository(
    protected val connectionFactory: JdbcConnectionFactory
) extends PaifuRepository
    with JdbcRepositorySupport:
  override def save(paifu: Paifu): Paifu =
    val sql =
      """
        |insert into paifus (id, table_id, tournament_id, stage_id, recorded_at, player_ids, payload, updated_at)
        |values (?, ?, ?, ?, ?, ?, cast(? as jsonb), now())
        |on conflict (id) do update set
        |  table_id = excluded.table_id,
        |  tournament_id = excluded.tournament_id,
        |  stage_id = excluded.stage_id,
        |  recorded_at = excluded.recorded_at,
        |  player_ids = excluded.player_ids,
        |  payload = excluded.payload,
        |  updated_at = now()
        |""".stripMargin

    withConnection { connection =>
      Using.resource(connection.prepareStatement(sql)) { statement =>
        statement.setString(1, paifu.id.value)
        statement.setString(2, paifu.metadata.tableId.value)
        statement.setString(3, paifu.metadata.tournamentId.value)
        statement.setString(4, paifu.metadata.stageId.value)
        statement.setTimestamp(5, Timestamp.from(paifu.metadata.recordedAt))
        statement.setArray(
          6,
          connection.createArrayOf("text", paifu.playerIds.map(_.value).toArray)
        )
        statement.setString(7, writeJson(paifu))
        statement.executeUpdate()
      }
    }

    paifu

  override def findById(id: PaifuId): Option[Paifu] =
    readOne[Paifu]("select payload from paifus where id = ?", { statement =>
      statement.setString(1, id.value)
    })

  override def findAll(): Vector[Paifu] =
    readAll[Paifu]("select payload from paifus order by recorded_at desc")

  override def findByPlayer(playerId: PlayerId): Vector[Paifu] =
    readAll[Paifu](
      "select payload from paifus where ? = any(player_ids) order by recorded_at desc",
      { statement =>
        statement.setString(1, playerId.value)
      }
    )

final class PostgresAppealTicketRepository(
    protected val connectionFactory: JdbcConnectionFactory
) extends AppealTicketRepository
    with JdbcRepositorySupport:
  override def save(ticket: AppealTicket): AppealTicket =
    val persisted = ticket.copy(version = ticket.version + 1)
    val sql =
      """
        |insert into appeal_tickets (id, table_id, tournament_id, stage_id, status, opened_by, payload, updated_at)
        |values (?, ?, ?, ?, ?, ?, cast(? as jsonb), now())
        |on conflict (id) do update set
        |  table_id = excluded.table_id,
        |  tournament_id = excluded.tournament_id,
        |  stage_id = excluded.stage_id,
        |  status = excluded.status,
        |  opened_by = excluded.opened_by,
        |  payload = excluded.payload,
        |  updated_at = now()
        |where cast(appeal_tickets.payload ->> 'version' as integer) = ?
        |""".stripMargin

    val rowsUpdated = withConnection { connection =>
      Using.resource(connection.prepareStatement(sql)) { statement =>
        statement.setString(1, persisted.id.value)
        statement.setString(2, persisted.tableId.value)
        statement.setString(3, persisted.tournamentId.value)
        statement.setString(4, persisted.stageId.value)
        statement.setString(5, persisted.status.toString)
        statement.setString(6, persisted.openedBy.value)
        statement.setString(7, writeJson(persisted))
        statement.setInt(8, ticket.version)
        statement.executeUpdate()
      }
    }

    requireOptimisticUpdate(rowsUpdated, "appeal-ticket", persisted.id.value, ticket.version, findById(persisted.id).map(_.version))
    persisted

  override def findById(id: AppealTicketId): Option[AppealTicket] =
    readOne[AppealTicket]("select payload from appeal_tickets where id = ?", { statement =>
      statement.setString(1, id.value)
    })

  override def findAll(): Vector[AppealTicket] =
    readAll[AppealTicket]("select payload from appeal_tickets order by updated_at desc")

final class PostgresDashboardRepository(
    protected val connectionFactory: JdbcConnectionFactory
) extends DashboardRepository
    with JdbcRepositorySupport:
  override def save(dashboard: Dashboard): Dashboard =
    val persisted = dashboard.copy(version = dashboard.version + 1)
    val sql =
      """
        |insert into dashboards (owner_key, owner_type, payload, updated_at)
        |values (?, ?, cast(? as jsonb), now())
        |on conflict (owner_key) do update set
        |  owner_type = excluded.owner_type,
        |  payload = excluded.payload,
        |  updated_at = now()
        |where cast(dashboards.payload ->> 'version' as integer) = ?
        |""".stripMargin

    val rowsUpdated = withConnection { connection =>
      Using.resource(connection.prepareStatement(sql)) { statement =>
        statement.setString(1, ownerKey(persisted.owner))
        statement.setString(2, ownerType(persisted.owner))
        statement.setString(3, writeJson(persisted))
        statement.setInt(4, dashboard.version)
        statement.executeUpdate()
      }
    }

    requireOptimisticUpdate(rowsUpdated, "dashboard", ownerKey(persisted.owner), dashboard.version, findByOwner(persisted.owner).map(_.version))
    persisted

  override def findByOwner(owner: DashboardOwner): Option[Dashboard] =
    readOne[Dashboard]("select payload from dashboards where owner_key = ?", { statement =>
      statement.setString(1, ownerKey(owner))
    })

  override def findAll(): Vector[Dashboard] =
    readAll[Dashboard]("select payload from dashboards order by owner_key")

  private def ownerKey(owner: DashboardOwner): String =
    owner match
      case DashboardOwner.Player(playerId) => s"player:${playerId.value}"
      case DashboardOwner.Club(clubId)     => s"club:${clubId.value}"

  private def ownerType(owner: DashboardOwner): String =
    owner match
      case DashboardOwner.Player(_) => "player"
      case DashboardOwner.Club(_)   => "club"

final class PostgresAdvancedStatsBoardRepository(
    protected val connectionFactory: JdbcConnectionFactory
) extends AdvancedStatsBoardRepository
    with JdbcRepositorySupport:
  override def save(board: AdvancedStatsBoard): AdvancedStatsBoard =
    val persisted = board.copy(version = board.version + 1)
    val sql =
      """
        |insert into advanced_stats_boards (owner_key, owner_type, payload, updated_at)
        |values (?, ?, cast(? as jsonb), now())
        |on conflict (owner_key) do update set
        |  owner_type = excluded.owner_type,
        |  payload = excluded.payload,
        |  updated_at = now()
        |where cast(advanced_stats_boards.payload ->> 'version' as integer) = ?
        |""".stripMargin

    val rowsUpdated = withConnection { connection =>
      Using.resource(connection.prepareStatement(sql)) { statement =>
        statement.setString(1, ownerKey(persisted.owner))
        statement.setString(2, ownerType(persisted.owner))
        statement.setString(3, writeJson(persisted))
        statement.setInt(4, board.version)
        statement.executeUpdate()
      }
    }

    requireOptimisticUpdate(rowsUpdated, "advanced-stats-board", ownerKey(persisted.owner), board.version, findByOwner(persisted.owner).map(_.version))
    persisted

  override def findByOwner(owner: DashboardOwner): Option[AdvancedStatsBoard] =
    readOne[AdvancedStatsBoard]("select payload from advanced_stats_boards where owner_key = ?", {
      statement =>
        statement.setString(1, ownerKey(owner))
    })

  override def findAll(): Vector[AdvancedStatsBoard] =
    readAll[AdvancedStatsBoard]("select payload from advanced_stats_boards order by owner_key")

  private def ownerKey(owner: DashboardOwner): String =
    owner match
      case DashboardOwner.Player(playerId) => s"player:${playerId.value}"
      case DashboardOwner.Club(clubId)     => s"club:${clubId.value}"

  private def ownerType(owner: DashboardOwner): String =
    owner match
      case DashboardOwner.Player(_) => "player"
      case DashboardOwner.Club(_)   => "club"

final class PostgresAdvancedStatsRecomputeTaskRepository(
    protected val connectionFactory: JdbcConnectionFactory
) extends AdvancedStatsRecomputeTaskRepository
    with JdbcRepositorySupport:
  override def save(task: AdvancedStatsRecomputeTask): AdvancedStatsRecomputeTask =
    val persisted = task.copy(version = task.version + 1)
    val sql =
      """
        |insert into advanced_stats_recompute_tasks (
        |  id,
        |  owner_key,
        |  owner_type,
        |  status,
        |  calculator_version,
        |  requested_at,
        |  payload,
        |  updated_at
        |)
        |values (?, ?, ?, ?, ?, ?, cast(? as jsonb), now())
        |on conflict (id) do update set
        |  owner_key = excluded.owner_key,
        |  owner_type = excluded.owner_type,
        |  status = excluded.status,
        |  calculator_version = excluded.calculator_version,
        |  requested_at = excluded.requested_at,
        |  payload = excluded.payload,
        |  updated_at = now()
        |where cast(advanced_stats_recompute_tasks.payload ->> 'version' as integer) = ?
        |""".stripMargin

    val rowsUpdated = withConnection { connection =>
      Using.resource(connection.prepareStatement(sql)) { statement =>
        statement.setString(1, persisted.id.value)
        statement.setString(2, ownerKey(persisted.owner))
        statement.setString(3, ownerType(persisted.owner))
        statement.setString(4, persisted.status.toString)
        statement.setInt(5, persisted.calculatorVersion)
        statement.setTimestamp(6, Timestamp.from(persisted.requestedAt))
        statement.setString(7, writeJson(persisted))
        statement.setInt(8, task.version)
        statement.executeUpdate()
      }
    }

    requireOptimisticUpdate(rowsUpdated, "advanced-stats-task", persisted.id.value, task.version, findById(persisted.id).map(_.version))
    persisted

  override def findById(id: AdvancedStatsRecomputeTaskId): Option[AdvancedStatsRecomputeTask] =
    readOne[AdvancedStatsRecomputeTask](
      "select payload from advanced_stats_recompute_tasks where id = ?",
      { statement =>
        statement.setString(1, id.value)
      }
    )

  override def findAll(): Vector[AdvancedStatsRecomputeTask] =
    readAll[AdvancedStatsRecomputeTask](
      "select payload from advanced_stats_recompute_tasks order by requested_at, id"
    )

  override def findPending(limit: Int, asOf: java.time.Instant = java.time.Instant.now()): Vector[AdvancedStatsRecomputeTask] =
    readAll[AdvancedStatsRecomputeTask](
      "select payload from advanced_stats_recompute_tasks where status = ? order by requested_at, id",
      { statement =>
        statement.setString(1, AdvancedStatsRecomputeTaskStatus.Pending.toString)
      }
    )
      .filter(_.isRunnable(asOf))
      .take(limit)

  override def findActiveByOwner(
      owner: DashboardOwner,
      calculatorVersion: Int
  ): Option[AdvancedStatsRecomputeTask] =
    readOne[AdvancedStatsRecomputeTask](
      """
        |select payload
        |from advanced_stats_recompute_tasks
        |where owner_key = ?
        |  and calculator_version = ?
        |  and status in (?, ?)
        |order by requested_at
        |limit 1
        |""".stripMargin,
      { statement =>
        statement.setString(1, ownerKey(owner))
        statement.setInt(2, calculatorVersion)
        statement.setString(3, AdvancedStatsRecomputeTaskStatus.Pending.toString)
        statement.setString(4, AdvancedStatsRecomputeTaskStatus.Processing.toString)
      }
    )

  private def ownerKey(owner: DashboardOwner): String =
    owner match
      case DashboardOwner.Player(playerId) => s"player:${playerId.value}"
      case DashboardOwner.Club(clubId)     => s"club:${clubId.value}"

  private def ownerType(owner: DashboardOwner): String =
    owner match
      case DashboardOwner.Player(_) => "player"
      case DashboardOwner.Club(_)   => "club"

final class PostgresGlobalDictionaryRepository(
    protected val connectionFactory: JdbcConnectionFactory
) extends GlobalDictionaryRepository
    with JdbcRepositorySupport:
  override def save(entry: GlobalDictionaryEntry): GlobalDictionaryEntry =
    val persisted = entry.copy(version = entry.version + 1)
    val sql =
      """
        |insert into global_dictionary (key, updated_at, payload)
        |values (?, ?, cast(? as jsonb))
        |on conflict (key) do update set
        |  updated_at = excluded.updated_at,
        |  payload = excluded.payload
        |where cast(global_dictionary.payload ->> 'version' as integer) = ?
        |""".stripMargin

    val rowsUpdated = withConnection { connection =>
      Using.resource(connection.prepareStatement(sql)) { statement =>
        statement.setString(1, persisted.key)
        statement.setTimestamp(2, Timestamp.from(persisted.updatedAt))
        statement.setString(3, writeJson(persisted))
        statement.setInt(4, entry.version)
        statement.executeUpdate()
      }
    }

    requireOptimisticUpdate(rowsUpdated, "global-dictionary-entry", persisted.key, entry.version, findByKey(persisted.key).map(_.version))
    persisted

  override def findByKey(key: String): Option[GlobalDictionaryEntry] =
    readOne[GlobalDictionaryEntry](
      "select payload from global_dictionary where key = ?",
      { statement =>
        statement.setString(1, key)
      }
    )

  override def findAll(): Vector[GlobalDictionaryEntry] =
    readAll[GlobalDictionaryEntry]("select payload from global_dictionary order by key")

final class PostgresDictionaryNamespaceRepository(
    protected val connectionFactory: JdbcConnectionFactory
) extends DictionaryNamespaceRepository
    with JdbcRepositorySupport:
  override def save(registration: DictionaryNamespaceRegistration): DictionaryNamespaceRegistration =
    val persisted = registration.copy(version = registration.version + 1)
    val sql =
      """
        |insert into dictionary_namespaces (namespace_prefix, owner_player_id, status, requested_at, payload, updated_at)
        |values (?, ?, ?, ?, cast(? as jsonb), now())
        |on conflict (namespace_prefix) do update set
        |  owner_player_id = excluded.owner_player_id,
        |  status = excluded.status,
        |  requested_at = excluded.requested_at,
        |  payload = excluded.payload,
        |  updated_at = now()
        |where cast(dictionary_namespaces.payload ->> 'version' as integer) = ?
        |""".stripMargin

    val rowsUpdated = withConnection { connection =>
      Using.resource(connection.prepareStatement(sql)) { statement =>
        statement.setString(1, persisted.namespacePrefix)
        statement.setString(2, persisted.ownerPlayerId.value)
        statement.setString(3, persisted.status.toString)
        statement.setTimestamp(4, Timestamp.from(persisted.requestedAt))
        statement.setString(5, writeJson(persisted))
        statement.setInt(6, registration.version)
        statement.executeUpdate()
      }
    }

    requireOptimisticUpdate(rowsUpdated, "dictionary-namespace", persisted.namespacePrefix, registration.version, findByPrefix(persisted.namespacePrefix).map(_.version))
    persisted

  override def findByPrefix(prefix: String): Option[DictionaryNamespaceRegistration] =
    readOne[DictionaryNamespaceRegistration](
      "select payload from dictionary_namespaces where namespace_prefix = ?",
      { statement => statement.setString(1, prefix) }
    )

  override def findAll(): Vector[DictionaryNamespaceRegistration] =
    readAll[DictionaryNamespaceRegistration](
      "select payload from dictionary_namespaces order by namespace_prefix"
    )

final class PostgresTournamentSettlementRepository(
    protected val connectionFactory: JdbcConnectionFactory
) extends TournamentSettlementRepository
    with JdbcRepositorySupport:
  override def save(snapshot: TournamentSettlementSnapshot): TournamentSettlementSnapshot =
    val persisted = snapshot.copy(version = snapshot.version + 1)
    val sql =
      """
        |insert into tournament_settlements (id, tournament_id, stage_id, generated_at, payload, updated_at)
        |values (?, ?, ?, ?, cast(? as jsonb), now())
        |on conflict (id) do update set
        |  tournament_id = excluded.tournament_id,
        |  stage_id = excluded.stage_id,
        |  generated_at = excluded.generated_at,
        |  payload = excluded.payload,
        |  updated_at = now()
        |where cast(tournament_settlements.payload ->> 'version' as integer) = ?
        |""".stripMargin

    val rowsUpdated = withConnection { connection =>
      Using.resource(connection.prepareStatement(sql)) { statement =>
        statement.setString(1, persisted.id.value)
        statement.setString(2, persisted.tournamentId.value)
        statement.setString(3, persisted.stageId.value)
        statement.setTimestamp(4, Timestamp.from(persisted.generatedAt))
        statement.setString(5, writeJson(persisted))
        statement.setInt(6, snapshot.version)
        statement.executeUpdate()
      }
    }

    requireOptimisticUpdate(rowsUpdated, "tournament-settlement", persisted.id.value, snapshot.version, findById(persisted.id).map(_.version))
    persisted

  override def findById(id: SettlementSnapshotId): Option[TournamentSettlementSnapshot] =
    readOne[TournamentSettlementSnapshot](
      "select payload from tournament_settlements where id = ?",
      { statement =>
        statement.setString(1, id.value)
      }
    )

  override def findByTournamentAndStage(
      tournamentId: TournamentId,
      stageId: TournamentStageId
  ): Option[TournamentSettlementSnapshot] =
    readOne[TournamentSettlementSnapshot](
      """
        |select payload
        |from tournament_settlements
        |where tournament_id = ? and stage_id = ?
        |order by generated_at desc, id desc
        |limit 1
        |""".stripMargin,
      { statement =>
        statement.setString(1, tournamentId.value)
        statement.setString(2, stageId.value)
      }
    )

  override def findByTournament(tournamentId: TournamentId): Vector[TournamentSettlementSnapshot] =
    readAll[TournamentSettlementSnapshot](
      "select payload from tournament_settlements where tournament_id = ? order by generated_at desc",
      { statement =>
        statement.setString(1, tournamentId.value)
      }
    )

  override def findAll(): Vector[TournamentSettlementSnapshot] =
    readAll[TournamentSettlementSnapshot](
      "select payload from tournament_settlements order by generated_at desc"
    )

final class PostgresEventCascadeRecordRepository(
    protected val connectionFactory: JdbcConnectionFactory
) extends EventCascadeRecordRepository
    with JdbcRepositorySupport:
  override def save(record: EventCascadeRecord): EventCascadeRecord =
    val persisted = record.copy(version = record.version + 1)
    val sql =
      """
        |insert into event_cascade_records (id, consumer, status, aggregate_type, aggregate_id, occurred_at, payload, updated_at)
        |values (?, ?, ?, ?, ?, ?, cast(? as jsonb), now())
        |on conflict (id) do update set
        |  consumer = excluded.consumer,
        |  status = excluded.status,
        |  aggregate_type = excluded.aggregate_type,
        |  aggregate_id = excluded.aggregate_id,
        |  occurred_at = excluded.occurred_at,
        |  payload = excluded.payload,
        |  updated_at = now()
        |where cast(event_cascade_records.payload ->> 'version' as integer) = ?
        |""".stripMargin

    val rowsUpdated = withConnection { connection =>
      Using.resource(connection.prepareStatement(sql)) { statement =>
        statement.setString(1, persisted.id.value)
        statement.setString(2, persisted.consumer.toString)
        statement.setString(3, persisted.status.toString)
        statement.setString(4, persisted.aggregateType)
        statement.setString(5, persisted.aggregateId)
        statement.setTimestamp(6, Timestamp.from(persisted.occurredAt))
        statement.setString(7, writeJson(persisted))
        statement.setInt(8, record.version)
        statement.executeUpdate()
      }
    }

    requireOptimisticUpdate(rowsUpdated, "event-cascade-record", persisted.id.value, record.version, findById(persisted.id).map(_.version))
    persisted

  override def findById(id: EventCascadeRecordId): Option[EventCascadeRecord] =
    readOne[EventCascadeRecord](
      "select payload from event_cascade_records where id = ?",
      { statement =>
        statement.setString(1, id.value)
      }
    )

  override def findAll(): Vector[EventCascadeRecord] =
    readAll[EventCascadeRecord](
      "select payload from event_cascade_records order by occurred_at desc, id desc"
    )

final class PostgresAuditEventRepository(
    protected val connectionFactory: JdbcConnectionFactory
) extends AuditEventRepository
    with JdbcRepositorySupport:
  override def save(entry: AuditEventEntry): AuditEventEntry =
    val sql =
      """
        |insert into audit_events (id, aggregate_type, aggregate_id, event_type, occurred_at, actor_id, payload)
        |values (?, ?, ?, ?, ?, ?, cast(? as jsonb))
        |on conflict (id) do update set
        |  aggregate_type = excluded.aggregate_type,
        |  aggregate_id = excluded.aggregate_id,
        |  event_type = excluded.event_type,
        |  occurred_at = excluded.occurred_at,
        |  actor_id = excluded.actor_id,
        |  payload = excluded.payload
        |""".stripMargin

    withConnection { connection =>
      Using.resource(connection.prepareStatement(sql)) { statement =>
        statement.setString(1, entry.id.value)
        statement.setString(2, entry.aggregateType)
        statement.setString(3, entry.aggregateId)
        statement.setString(4, entry.eventType)
        statement.setTimestamp(5, Timestamp.from(entry.occurredAt))
        setNullableString(statement, 6, entry.actorId.map(_.value))
        statement.setString(7, writeJson(entry))
        statement.executeUpdate()
      }
    }

    entry

  override def findByAggregate(aggregateType: String, aggregateId: String): Vector[AuditEventEntry] =
    readAll[AuditEventEntry](
      "select payload from audit_events where aggregate_type = ? and aggregate_id = ? order by occurred_at desc",
      { statement =>
        statement.setString(1, aggregateType)
        statement.setString(2, aggregateId)
      }
    )

  override def findAll(): Vector[AuditEventEntry] =
    readAll[AuditEventEntry]("select payload from audit_events order by occurred_at desc")

final class PostgresDomainEventOutboxRepository(
    protected val connectionFactory: JdbcConnectionFactory
) extends DomainEventOutboxRepository
    with JdbcRepositorySupport:
  override def save(record: DomainEventOutboxRecord): DomainEventOutboxRecord =
    val persisted = record.copy(
      sequenceNo =
        if record.sequenceNo > 0L then record.sequenceNo
        else nextSequenceNo(),
      version = record.version + 1
    )
    val sql =
      """
        |insert into domain_event_outbox (
        |  id,
        |  sequence_no,
        |  event_type,
        |  aggregate_type,
        |  aggregate_id,
        |  status,
        |  occurred_at,
        |  available_at,
        |  payload,
        |  updated_at
        |)
        |values (?, ?, ?, ?, ?, ?, ?, ?, cast(? as jsonb), now())
        |on conflict (id) do update set
        |  sequence_no = excluded.sequence_no,
        |  event_type = excluded.event_type,
        |  aggregate_type = excluded.aggregate_type,
        |  aggregate_id = excluded.aggregate_id,
        |  status = excluded.status,
        |  occurred_at = excluded.occurred_at,
        |  available_at = excluded.available_at,
        |  payload = excluded.payload,
        |  updated_at = now()
        |where cast(domain_event_outbox.payload ->> 'version' as integer) = ?
        |""".stripMargin

    val rowsUpdated = withConnection { connection =>
      Using.resource(connection.prepareStatement(sql)) { statement =>
        statement.setString(1, persisted.id.value)
        statement.setLong(2, persisted.sequenceNo)
        statement.setString(3, persisted.eventType)
        statement.setString(4, persisted.aggregateType)
        statement.setString(5, persisted.aggregateId)
        statement.setString(6, persisted.status.toString)
        statement.setTimestamp(7, Timestamp.from(persisted.occurredAt))
        statement.setTimestamp(8, Timestamp.from(persisted.availableAt))
        statement.setString(9, writeJson(persisted))
        statement.setInt(10, record.version)
        statement.executeUpdate()
      }
    }

    requireOptimisticUpdate(rowsUpdated, "domain-event-outbox-record", persisted.id.value, record.version, findById(persisted.id).map(_.version))
    persisted

  override def findById(id: DomainEventOutboxRecordId): Option[DomainEventOutboxRecord] =
    readOne[DomainEventOutboxRecord](
      "select payload from domain_event_outbox where id = ?",
      { statement =>
        statement.setString(1, id.value)
      }
    )

  override def findAll(): Vector[DomainEventOutboxRecord] =
    readAll[DomainEventOutboxRecord](
      "select payload from domain_event_outbox order by sequence_no asc"
    )

  override def findPending(limit: Int, asOf: java.time.Instant = java.time.Instant.now()): Vector[DomainEventOutboxRecord] =
    readAll[DomainEventOutboxRecord](
      """
        |select payload
        |from domain_event_outbox
        |where status = 'Pending' and available_at <= ?
        |order by sequence_no asc
        |limit ?
        |""".stripMargin,
      { statement =>
        statement.setTimestamp(1, Timestamp.from(asOf))
        statement.setInt(2, limit)
      }
    )

  private def nextSequenceNo(): Long =
    withConnection { connection =>
      Using.resource(connection.prepareStatement("select nextval('domain_event_outbox_sequence') as sequence_no")) {
        statement =>
          Using.resource(statement.executeQuery()) { resultSet =>
            resultSet.next()
            resultSet.getLong("sequence_no")
          }
      }
    }

final class PostgresDomainEventDeliveryReceiptRepository(
    protected val connectionFactory: JdbcConnectionFactory
) extends DomainEventDeliveryReceiptRepository
    with JdbcRepositorySupport:
  override def save(receipt: DomainEventDeliveryReceipt): DomainEventDeliveryReceipt =
    val persisted = receipt.copy(version = receipt.version + 1)
    val sql =
      """
        |insert into domain_event_delivery_receipts (
        |  id,
        |  outbox_record_id,
        |  subscriber_id,
        |  event_type,
        |  delivered_at,
        |  payload,
        |  updated_at
        |)
        |values (?, ?, ?, ?, ?, cast(? as jsonb), now())
        |on conflict (outbox_record_id, subscriber_id) do update set
        |  event_type = domain_event_delivery_receipts.event_type,
        |  delivered_at = domain_event_delivery_receipts.delivered_at,
        |  payload = domain_event_delivery_receipts.payload,
        |  updated_at = domain_event_delivery_receipts.updated_at
        |returning payload
        |""".stripMargin

    withConnection { connection =>
      Using.resource(connection.prepareStatement(sql)) { statement =>
        statement.setString(1, persisted.id.value)
        statement.setString(2, persisted.outboxRecordId.value)
        statement.setString(3, persisted.subscriberId)
        statement.setString(4, persisted.eventType)
        statement.setTimestamp(5, Timestamp.from(persisted.deliveredAt))
        statement.setString(6, writeJson(persisted))
        Using.resource(statement.executeQuery()) { resultSet =>
          resultSet.next()
          read[DomainEventDeliveryReceipt](resultSet.getString("payload"))
        }
      }
    }

  override def findById(id: DomainEventDeliveryReceiptId): Option[DomainEventDeliveryReceipt] =
    readOne[DomainEventDeliveryReceipt](
      "select payload from domain_event_delivery_receipts where id = ?",
      { statement =>
        statement.setString(1, id.value)
      }
    )

  override def findAll(): Vector[DomainEventDeliveryReceipt] =
    readAll[DomainEventDeliveryReceipt](
      "select payload from domain_event_delivery_receipts order by delivered_at desc, id desc"
    )

  override def findByOutboxRecordAndSubscriber(
      outboxRecordId: DomainEventOutboxRecordId,
      subscriberId: String
  ): Option[DomainEventDeliveryReceipt] =
    readOne[DomainEventDeliveryReceipt](
      """
        |select payload
        |from domain_event_delivery_receipts
        |where outbox_record_id = ? and subscriber_id = ?
        |limit 1
        |""".stripMargin,
      { statement =>
        statement.setString(1, outboxRecordId.value)
        statement.setString(2, subscriberId)
      }
    )

final class PostgresDomainEventSubscriberCursorRepository(
    protected val connectionFactory: JdbcConnectionFactory
) extends DomainEventSubscriberCursorRepository
    with JdbcRepositorySupport:
  override def save(cursor: DomainEventSubscriberCursor): DomainEventSubscriberCursor =
    val persisted = cursor.copy(version = cursor.version + 1)
    val sql =
      """
        |insert into domain_event_subscriber_cursors (
        |  id,
        |  subscriber_id,
        |  partition_key,
        |  last_outbox_record_id,
        |  last_sequence_no,
        |  advanced_at,
        |  payload,
        |  updated_at
        |)
        |values (?, ?, ?, ?, ?, ?, cast(? as jsonb), now())
        |on conflict (subscriber_id, partition_key) do update set
        |  last_outbox_record_id = excluded.last_outbox_record_id,
        |  last_sequence_no = excluded.last_sequence_no,
        |  advanced_at = excluded.advanced_at,
        |  payload = excluded.payload,
        |  updated_at = now()
        |where cast(domain_event_subscriber_cursors.payload ->> 'version' as integer) = ?
        |returning payload
        |""".stripMargin

    withConnection { connection =>
      Using.resource(connection.prepareStatement(sql)) { statement =>
        statement.setString(1, persisted.id.value)
        statement.setString(2, persisted.subscriberId)
        statement.setString(3, persisted.partitionKey)
        statement.setString(4, persisted.lastDeliveredOutboxRecordId.value)
        statement.setLong(5, persisted.lastDeliveredSequenceNo)
        statement.setTimestamp(6, Timestamp.from(persisted.advancedAt))
        statement.setString(7, writeJson(persisted))
        statement.setInt(8, cursor.version)
        Using.resource(statement.executeQuery()) { resultSet =>
          if resultSet.next() then read[DomainEventSubscriberCursor](resultSet.getString("payload"))
          else
            throw OptimisticConcurrencyException(
              aggregateType = "domain-event-subscriber-cursor",
              aggregateId = s"${cursor.subscriberId}:${cursor.partitionKey}",
              expectedVersion = cursor.version,
              actualVersion = findBySubscriberAndPartition(cursor.subscriberId, cursor.partitionKey).map(_.version)
            )
        }
      }
    }

  override def findById(id: DomainEventSubscriberCursorId): Option[DomainEventSubscriberCursor] =
    readOne[DomainEventSubscriberCursor](
      "select payload from domain_event_subscriber_cursors where id = ?",
      { statement =>
        statement.setString(1, id.value)
      }
    )

  override def findAll(): Vector[DomainEventSubscriberCursor] =
    readAll[DomainEventSubscriberCursor](
      "select payload from domain_event_subscriber_cursors order by subscriber_id asc, partition_key asc"
    )

  override def findBySubscriberAndPartition(
      subscriberId: String,
      partitionKey: String
  ): Option[DomainEventSubscriberCursor] =
    readOne[DomainEventSubscriberCursor](
      """
        |select payload
        |from domain_event_subscriber_cursors
        |where subscriber_id = ? and partition_key = ?
        |limit 1
        |""".stripMargin,
      { statement =>
        statement.setString(1, subscriberId)
        statement.setString(2, partitionKey)
      }
    )

final class JdbcTransactionManager(
    connectionFactory: JdbcConnectionFactory
) extends TransactionManager:
  override def inTransaction[A](operation: => A): A =
    connectionFactory.inTransaction(operation)

final class PostgresAdminService(connectionFactory: JdbcConnectionFactory):
  private val managedTables =
    Vector(
      "players",
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
