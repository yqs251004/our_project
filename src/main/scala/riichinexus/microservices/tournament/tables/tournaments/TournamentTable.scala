package riichinexus.microservices.tournament.tables.tournaments

import java.sql.{Connection, PreparedStatement, ResultSet, SQLException, Types}

import scala.annotation.tailrec
import scala.util.Using

import org.postgresql.util.PSQLException
import riichinexus.system.errors.OptimisticConcurrencyException
import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.microservices.club.objects.clubmanagement.ClubId
import riichinexus.microservices.tournament.objects.identity.TournamentId
import riichinexus.microservices.tournament.domain.competition.functions.TournamentDefaultsFunctions
import riichinexus.microservices.tournament.domain.competition.model.Tournament
import riichinexus.microservices.tournament.objects.competition.TournamentStatus
import riichinexus.system.json.JsonCodecs.given
import upickle.default.{read, write}

object TournamentTable:
  private val upsertSql: String =
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

  private[tournament] def save(connection: Connection, tournament: Tournament): Tournament =
    def persist(candidate: Tournament): Tournament =
      val persisted = candidate.copy(version = candidate.version + 1)
      val rowsUpdated = Using.resource(connection.prepareStatement(upsertSql)) { statement =>
        statement.setString(1, persisted.id.value)
        statement.setString(2, persisted.name)
        statement.setString(3, persisted.organizer)
        statement.setString(4, persisted.status.toString)
        statement.setString(5, write[Tournament](persisted))
        statement.setInt(6, candidate.version)
        statement.executeUpdate()
      }
      if rowsUpdated == 0 then
        throw OptimisticConcurrencyException(
          aggregateType = "tournament",
          aggregateId = persisted.id.value,
          expectedVersion = candidate.version,
          actualVersion = findById(connection, persisted.id).map(_.version)
        )
      persisted

    val normalizedTournament = TournamentDefaultsFunctions.ensureInitialStage(tournament)
    try persist(normalizedTournament)
    catch
      case error: SQLException if isUniqueViolation(error, "idx_tournaments_name_start") =>
        val normalized = findByNameAndOrganizer(connection, tournament.name, tournament.organizer)
          .map(existing => normalizedTournament.copy(id = existing.id, version = existing.version))
          .getOrElse(throw error)
        persist(normalized)

  private val findByIdSql: String =
    """
      |select payload
      |from tournaments
      |where id = ?
      |""".stripMargin

  private[tournament] def findById(connection: Connection, id: TournamentId): Option[Tournament] =
    Using.resource(connection.prepareStatement(findByIdSql)) { statement =>
      statement.setString(1, id.value)
      Using.resource(statement.executeQuery()) { resultSet =>
        if resultSet.next() then Some(normalizeOnRead(connection, readTournament(resultSet)))
        else None
      }
    }

  private val findByNameAndOrganizerSql: String =
    """
      |select payload
      |from tournaments
      |where name = ? and organizer = ?
      |""".stripMargin

  private[tournament] def findByNameAndOrganizer(
      connection: Connection,
      name: String,
      organizer: String
  ): Option[Tournament] =
    Using.resource(connection.prepareStatement(findByNameAndOrganizerSql)) { statement =>
      statement.setString(1, name)
      statement.setString(2, organizer)
      Using.resource(statement.executeQuery()) { resultSet =>
        if resultSet.next() then Some(normalizeOnRead(connection, readTournament(resultSet)))
        else None
      }
    }

  private val findByIdsSql: String =
    """
      |select payload
      |from tournaments
      |where id = any(?)
      |order by updated_at desc
      |""".stripMargin

  private[tournament] def findByIds(connection: Connection, ids: Vector[TournamentId]): Vector[Tournament] =
    if ids.isEmpty then Vector.empty
    else
      Using.resource(connection.prepareStatement(findByIdsSql)) { statement =>
        statement.setArray(
          1,
          connection.createArrayOf("text", ids.map(_.value).distinct.toArray)
        )
        Using.resource(statement.executeQuery())(readTournaments(connection, _))
      }

  private val findFilteredSql: String =
    """
      |select payload
      |from tournaments
      |where (? = true or status <> 'Draft')
      |  and (? is null or status = ?)
      |  and (? is null or payload @> cast(? as jsonb))
      |  and (? is null or lower(organizer) like ?)
      |order by updated_at desc
      |""".stripMargin

  private[tournament] def findFiltered(
      connection: Connection,
      status: Option[TournamentStatus] = None,
      adminId: Option[PlayerId] = None,
      organizer: Option[String] = None,
      includeDraft: Boolean = true
  ): Vector[Tournament] =
    Using.resource(connection.prepareStatement(findFilteredSql)) { statement =>
      statement.setBoolean(1, includeDraft)
      setNullableString(statement, 2, status.map(_.toString))
      setNullableString(statement, 3, status.map(_.toString))
      setNullableString(statement, 4, adminId.map(_.value))
      setNullableString(statement, 5, adminId.map(id => s"""{"admins":[{"value":"${id.value}"}]}"""))
      setNullableString(statement, 6, organizer)
      setNullableString(statement, 7, organizer.map(fragment => s"%${fragment.toLowerCase}%"))
      Using.resource(statement.executeQuery())(readTournaments(connection, _))
    }

  private val findByClubSql: String =
    """
      |select payload
      |from tournaments
      |where payload @> cast(? as jsonb)
      |   or payload @> cast(? as jsonb)
      |order by updated_at desc
      |""".stripMargin

  private[tournament] def findByClub(connection: Connection, clubId: ClubId): Vector[Tournament] =
    Using.resource(connection.prepareStatement(findByClubSql)) { statement =>
      statement.setString(1, s"""{"participatingClubs":["${clubId.value}"]}""")
      statement.setString(2, s"""{"whitelist":[{"clubId":"${clubId.value}"}]}""")
      Using.resource(statement.executeQuery())(readTournaments(connection, _))
    }

  private val findAllSql: String =
    """
      |select payload
      |from tournaments
      |order by updated_at desc
      |""".stripMargin

  private[tournament] def findAll(connection: Connection): Vector[Tournament] =
    Using.resource(connection.prepareStatement(findAllSql)) { statement =>
      Using.resource(statement.executeQuery())(readTournaments(connection, _))
    }

  private def normalizeOnRead(connection: Connection, tournament: Tournament): Tournament =
    if tournament.stages.nonEmpty then tournament
    else save(connection, TournamentDefaultsFunctions.ensureInitialStage(tournament))

  private def setNullableString(
      statement: PreparedStatement,
      index: Int,
      value: Option[String]
  ): Unit =
    value match
      case Some(actual) => statement.setString(index, actual)
      case None         => statement.setNull(index, Types.VARCHAR)

  private def readTournaments(connection: Connection, resultSet: ResultSet): Vector[Tournament] =
    @tailrec
    def loop(acc: Vector[Tournament]): Vector[Tournament] =
      if resultSet.next() then loop(normalizeOnRead(connection, readTournament(resultSet)) +: acc)
      else acc.reverse

    loop(Vector.empty)

  private def readTournament(resultSet: ResultSet): Tournament =
    read[Tournament](resultSet.getString("payload"))

  private def isUniqueViolation(error: SQLException, constraintName: String): Boolean =
    error match
      case postgresError: PSQLException =>
        Option(postgresError.getServerErrorMessage).exists(_.getConstraint == constraintName)
      case _ => false
