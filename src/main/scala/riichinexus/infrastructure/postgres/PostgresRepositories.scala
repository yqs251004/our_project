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

final class PostgresPlayerRepository(
    protected val connectionFactory: JdbcConnectionFactory
) extends PlayerRepository
    with JdbcRepositorySupport:
  override def save(player: Player): Player =
    try persist(player)
    catch
      case error: SQLException if PostgresErrors.isUniqueViolation(error, "idx_players_user_id") =>
        val normalized = findByUserId(player.userId)
          .map(existing => player.copy(id = existing.id, registeredAt = existing.registeredAt))
          .getOrElse(throw error)
        persist(normalized)

  private def persist(player: Player): Player =
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
        |""".stripMargin

    withConnection { connection =>
      Using.resource(connection.prepareStatement(sql)) { statement =>
        statement.setString(1, player.id.value)
        statement.setString(2, player.userId)
        statement.setString(3, player.nickname)
        setNullableString(statement, 4, player.clubId.map(_.value))
        statement.setInt(5, player.elo)
        statement.setString(6, writeJson(player))
        statement.executeUpdate()
      }
    }

    player

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
    val sql =
      """
        |insert into guest_sessions (id, created_at, display_name, payload, updated_at)
        |values (?, ?, ?, cast(? as jsonb), now())
        |on conflict (id) do update set
        |  created_at = excluded.created_at,
        |  display_name = excluded.display_name,
        |  payload = excluded.payload,
        |  updated_at = now()
        |""".stripMargin

    withConnection { connection =>
      Using.resource(connection.prepareStatement(sql)) { statement =>
        statement.setString(1, session.id.value)
        statement.setTimestamp(2, Timestamp.from(session.createdAt))
        statement.setString(3, session.displayName)
        statement.setString(4, writeJson(session))
        statement.executeUpdate()
      }
    }

    session

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
          .map(existing => club.copy(id = existing.id, creator = existing.creator, createdAt = existing.createdAt))
          .getOrElse(throw error)
        persist(normalized)

  private def persist(club: Club): Club =
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
        |""".stripMargin

    withConnection { connection =>
      Using.resource(connection.prepareStatement(sql)) { statement =>
        statement.setString(1, club.id.value)
        statement.setString(2, club.name)
        statement.setString(3, club.creator.value)
        statement.setInt(4, club.totalPoints)
        statement.setString(5, writeJson(club))
        statement.executeUpdate()
      }
    }

    club

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
          .map(existing => tournament.copy(id = existing.id))
          .getOrElse(throw error)
        persist(normalized)

  private def persist(tournament: Tournament): Tournament =
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
        |""".stripMargin

    withConnection { connection =>
      Using.resource(connection.prepareStatement(sql)) { statement =>
        statement.setString(1, tournament.id.value)
        statement.setString(2, tournament.name)
        statement.setString(3, tournament.organizer)
        statement.setString(4, tournament.status.toString)
        statement.setString(5, writeJson(tournament))
        statement.executeUpdate()
      }
    }

    tournament

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
        |""".stripMargin

    withConnection { connection =>
      Using.resource(connection.prepareStatement(sql)) { statement =>
        statement.setString(1, table.id.value)
        statement.setString(2, table.tournamentId.value)
        statement.setString(3, table.stageId.value)
        statement.setInt(4, table.tableNo)
        statement.setString(5, table.status.toString)
        statement.setString(6, writeJson(table))
        statement.executeUpdate()
      }
    }

    table

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
        |""".stripMargin

    withConnection { connection =>
      Using.resource(connection.prepareStatement(sql)) { statement =>
        statement.setString(1, ticket.id.value)
        statement.setString(2, ticket.tableId.value)
        statement.setString(3, ticket.tournamentId.value)
        statement.setString(4, ticket.stageId.value)
        statement.setString(5, ticket.status.toString)
        statement.setString(6, ticket.openedBy.value)
        statement.setString(7, writeJson(ticket))
        statement.executeUpdate()
      }
    }

    ticket

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
    val sql =
      """
        |insert into dashboards (owner_key, owner_type, payload, updated_at)
        |values (?, ?, cast(? as jsonb), now())
        |on conflict (owner_key) do update set
        |  owner_type = excluded.owner_type,
        |  payload = excluded.payload,
        |  updated_at = now()
        |""".stripMargin

    withConnection { connection =>
      Using.resource(connection.prepareStatement(sql)) { statement =>
        statement.setString(1, ownerKey(dashboard.owner))
        statement.setString(2, ownerType(dashboard.owner))
        statement.setString(3, writeJson(dashboard))
        statement.executeUpdate()
      }
    }

    dashboard

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

final class PostgresGlobalDictionaryRepository(
    protected val connectionFactory: JdbcConnectionFactory
) extends GlobalDictionaryRepository
    with JdbcRepositorySupport:
  override def save(entry: GlobalDictionaryEntry): GlobalDictionaryEntry =
    val sql =
      """
        |insert into global_dictionary (key, updated_at, payload)
        |values (?, ?, cast(? as jsonb))
        |on conflict (key) do update set
        |  updated_at = excluded.updated_at,
        |  payload = excluded.payload
        |""".stripMargin

    withConnection { connection =>
      Using.resource(connection.prepareStatement(sql)) { statement =>
        statement.setString(1, entry.key)
        statement.setTimestamp(2, Timestamp.from(entry.updatedAt))
        statement.setString(3, writeJson(entry))
        statement.executeUpdate()
      }
    }

    entry

  override def findByKey(key: String): Option[GlobalDictionaryEntry] =
    readOne[GlobalDictionaryEntry](
      "select payload from global_dictionary where key = ?",
      { statement =>
        statement.setString(1, key)
      }
    )

  override def findAll(): Vector[GlobalDictionaryEntry] =
    readAll[GlobalDictionaryEntry]("select payload from global_dictionary order by key")

final class PostgresTournamentSettlementRepository(
    protected val connectionFactory: JdbcConnectionFactory
) extends TournamentSettlementRepository
    with JdbcRepositorySupport:
  override def save(snapshot: TournamentSettlementSnapshot): TournamentSettlementSnapshot =
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
        |""".stripMargin

    withConnection { connection =>
      Using.resource(connection.prepareStatement(sql)) { statement =>
        statement.setString(1, snapshot.id.value)
        statement.setString(2, snapshot.tournamentId.value)
        statement.setString(3, snapshot.stageId.value)
        statement.setTimestamp(4, Timestamp.from(snapshot.generatedAt))
        statement.setString(5, writeJson(snapshot))
        statement.executeUpdate()
      }
    }

    snapshot

  override def findByTournamentAndStage(
      tournamentId: TournamentId,
      stageId: TournamentStageId
  ): Option[TournamentSettlementSnapshot] =
    readOne[TournamentSettlementSnapshot](
      "select payload from tournament_settlements where tournament_id = ? and stage_id = ?",
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
      "global_dictionary",
      "tournament_settlements",
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
        executeAdmin(connection, "truncate table tournament_settlements restart identity")
        executeAdmin(connection, "truncate table global_dictionary restart identity")
        executeAdmin(connection, "truncate table dashboards restart identity")
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
