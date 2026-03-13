package riichinexus.infrastructure.postgres

import java.sql.Connection
import java.sql.DriverManager
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.SQLException
import java.sql.Timestamp
import java.sql.Types

import scala.util.Using

import riichinexus.application.ports.*
import riichinexus.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import upickle.default.*

final class JdbcConnectionFactory(config: DatabaseConfig):
  private val driverClass = "org.postgresql.Driver"

  try Class.forName(driverClass)
  catch
    case _: ClassNotFoundException =>
      ()

  def withConnection[A](f: Connection => A): A =
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
          |  payload jsonb not null,
          |  updated_at timestamptz not null default now()
          |)
          |""".stripMargin
      )
      execute(connection, "create index if not exists idx_paifus_table_id on paifus (table_id)")
      execute(connection, "create index if not exists idx_paifus_recorded_at on paifus (recorded_at)")

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

  override def findAll(): Vector[Player] =
    readAll[Player]("select payload from players order by nickname")

final class PostgresClubRepository(
    protected val connectionFactory: JdbcConnectionFactory
) extends ClubRepository
    with JdbcRepositorySupport:
  override def save(club: Club): Club =
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

  override def findAll(): Vector[Club] =
    readAll[Club]("select payload from clubs order by name")

final class PostgresTournamentRepository(
    protected val connectionFactory: JdbcConnectionFactory
) extends TournamentRepository
    with JdbcRepositorySupport:
  override def save(tournament: Tournament): Tournament =
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
    })

  override def findAll(): Vector[Table] =
    readAll[Table]("select payload from tables order by updated_at desc")

final class PostgresPaifuRepository(
    protected val connectionFactory: JdbcConnectionFactory
) extends PaifuRepository
    with JdbcRepositorySupport:
  override def save(paifu: Paifu): Paifu =
    val sql =
      """
        |insert into paifus (id, table_id, tournament_id, stage_id, recorded_at, payload, updated_at)
        |values (?, ?, ?, ?, ?, cast(? as jsonb), now())
        |on conflict (id) do update set
        |  table_id = excluded.table_id,
        |  tournament_id = excluded.tournament_id,
        |  stage_id = excluded.stage_id,
        |  recorded_at = excluded.recorded_at,
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
        statement.setString(6, writeJson(paifu))
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
