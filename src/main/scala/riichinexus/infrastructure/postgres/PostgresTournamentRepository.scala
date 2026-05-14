package riichinexus.infrastructure.postgres

import java.sql.SQLException
import java.sql.Timestamp

import scala.util.Using

import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.application.ports.*
import riichinexus.domain.model.*
import upickle.default.read
final class PostgresTournamentRepository(
    protected val connectionFactory: JdbcConnectionFactory
) extends TournamentRepository
    with JdbcRepositorySupport:
  override def save(tournament: Tournament): Tournament =
    try persist(TournamentDefaults.ensureInitialStage(tournament))
    catch
      case error: SQLException if PostgresErrors.isUniqueViolation(error, "idx_tournaments_name_start") =>
        val normalized = findByNameAndOrganizer(tournament.name, tournament.organizer)
          .map(existing =>
            TournamentDefaults.ensureInitialStage(tournament).copy(id = existing.id, version = existing.version)
          )
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
        statement.setString(5, writeJson[Tournament](persisted))
        statement.setInt(6, tournament.version)
        statement.executeUpdate()
      }
    }

    requireOptimisticUpdate(
      rowsUpdated,
      "tournament",
      persisted.id.value,
      tournament.version,
      findById(persisted.id).map(_.version)
    )
    persisted

  override def findById(id: TournamentId): Option[Tournament] =
    readOne[Tournament]("select payload from tournaments where id = ?", { statement =>
      statement.setString(1, id.value)
    }).map(normalizeOnRead)

  override def findByNameAndOrganizer(name: String, organizer: String): Option[Tournament] =
    readOne[Tournament](
      "select payload from tournaments where name = ? and organizer = ?",
      { statement =>
        statement.setString(1, name)
        statement.setString(2, organizer)
      }
    ).map(normalizeOnRead)

  override def findByIds(ids: Vector[TournamentId]): Vector[Tournament] =
    if ids.isEmpty then Vector.empty
    else
      withConnection { connection =>
        Using.resource(
          connection.prepareStatement(
            """
              |select payload
              |from tournaments
              |where id = any(?)
              |order by updated_at desc
              |""".stripMargin
          )
        ) { statement =>
          statement.setArray(
            1,
            connection.createArrayOf("text", ids.map(_.value).distinct.toArray)
          )
          Using.resource(statement.executeQuery()) { resultSet =>
            val buffer = Vector.newBuilder[Tournament]
            while resultSet.next() do
              buffer += normalizeOnRead(read[Tournament](resultSet.getString("payload")))
            buffer.result()
          }
        }
      }

  override def findFiltered(
      status: Option[TournamentStatus] = None,
      adminId: Option[PlayerId] = None,
      organizer: Option[String] = None,
      includeDraft: Boolean = true
  ): Vector[Tournament] =
    readAll[Tournament](
      """
        |select payload
        |from tournaments
        |where (? = true or status <> 'Draft')
        |  and (? is null or status = ?)
        |  and (? is null or payload @> cast(? as jsonb))
        |  and (? is null or lower(organizer) like ?)
        |order by updated_at desc
        |""".stripMargin,
      { statement =>
        statement.setBoolean(1, includeDraft)
        setNullableString(statement, 2, status.map(_.toString))
        setNullableString(statement, 3, status.map(_.toString))
        setNullableString(statement, 4, adminId.map(_.value))
        setNullableString(statement, 5, adminId.map(id => s"""{"admins":[{"value":"${id.value}"}]}"""))
        setNullableString(statement, 6, organizer)
        setNullableString(statement, 7, organizer.map(fragment => s"%${fragment.toLowerCase}%"))
      }
    ).map(normalizeOnRead)

  override def findByClub(clubId: ClubId): Vector[Tournament] =
    readAll[Tournament](
      """
        |select payload
        |from tournaments
        |where payload @> cast(? as jsonb)
        |   or payload @> cast(? as jsonb)
        |order by updated_at desc
        |""".stripMargin,
      { statement =>
        statement.setString(1, s"""{"participatingClubs":["${clubId.value}"]}""")
        statement.setString(2, s"""{"whitelist":[{"clubId":"${clubId.value}"}]}""")
      }
    )

  override def findPublic(): Vector[Tournament] =
    findFiltered(includeDraft = false)

  override def findAll(): Vector[Tournament] =
    readAll[Tournament]("select payload from tournaments order by updated_at desc").map(normalizeOnRead)

  private def normalizeOnRead(tournament: Tournament): Tournament =
    if tournament.stages.nonEmpty then tournament
    else save(TournamentDefaults.ensureInitialStage(tournament))

object PostgresTournamentRepository:
  def apply(connectionFactory: JdbcConnectionFactory): PostgresTournamentRepository =
    new PostgresTournamentRepository(connectionFactory)
