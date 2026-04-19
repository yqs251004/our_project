package database.postgres

import java.sql.SQLException
import java.sql.Timestamp

import scala.util.Using

import org.postgresql.util.PSQLException

import json.JsonCodecs.given
import ports.*
import riichinexus.domain.model.*
import upickle.default.read

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

final class PostgresPlayerRepository(
    protected val connectionFactory: JdbcConnectionFactory
) extends PlayerRepository
    with JdbcRepositorySupport:
  override def save(player: Player): Player =
    try persist(player)
    catch
      case error: SQLException if PostgresErrors.isUniqueViolation(error, "idx_players_user_id") =>
        val normalized = findByUserId(player.userId)
          .map(existing =>
            player.copy(
              id = existing.id,
              registeredAt = existing.registeredAt,
              version = existing.version
            )
          )
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
        statement.setString(6, writeJson[Player](persisted))
        statement.setInt(7, player.version)
        statement.executeUpdate()
      }
    }

    requireOptimisticUpdate(
      rowsUpdated,
      "player",
      persisted.id.value,
      player.version,
      findById(persisted.id).map(_.version)
    )
    persisted

  override def findById(id: PlayerId): Option[Player] =
    readOne[Player]("select payload from players where id = ?", { statement =>
      statement.setString(1, id.value)
    })

  override def findByUserId(userId: String): Option[Player] =
    readOne[Player]("select payload from players where user_id = ?", { statement =>
      statement.setString(1, userId)
    })

  override def findByIds(ids: Vector[PlayerId]): Vector[Player] =
    if ids.isEmpty then Vector.empty
    else
      withConnection { connection =>
        Using.resource(
          connection.prepareStatement(
            """
              |select payload
              |from players
              |where id = any(?)
              |order by nickname asc
              |""".stripMargin
          )
        ) { statement =>
          statement.setArray(
            1,
            connection.createArrayOf("text", ids.map(_.value).distinct.toArray)
          )
          Using.resource(statement.executeQuery()) { resultSet =>
            val buffer = Vector.newBuilder[Player]
            while resultSet.next() do
              buffer += read[Player](resultSet.getString("payload"))
            buffer.result()
          }
        }
      }

  override def findByClub(clubId: ClubId): Vector[Player] =
    readAll[Player](
      "select payload from players where club_id = ? order by nickname",
      { statement =>
        statement.setString(1, clubId.value)
      }
    )

  override def findAll(): Vector[Player] =
    readAll[Player]("select payload from players order by nickname")

object PostgresPlayerRepository:
  def apply(connectionFactory: JdbcConnectionFactory): PostgresPlayerRepository =
    new PostgresPlayerRepository(connectionFactory)

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
        statement.setString(4, writeJson[GuestAccessSession](persisted))
        statement.setInt(5, session.version)
        statement.executeUpdate()
      }
    }

    requireOptimisticUpdate(
      rowsUpdated,
      "guest-session",
      persisted.id.value,
      session.version,
      findById(persisted.id).map(_.version)
    )
    persisted

  override def findById(id: GuestSessionId): Option[GuestAccessSession] =
    readOne[GuestAccessSession]("select payload from guest_sessions where id = ?", { statement =>
      statement.setString(1, id.value)
    })

  override def findAll(): Vector[GuestAccessSession] =
    readAll[GuestAccessSession]("select payload from guest_sessions order by created_at desc")

object PostgresGuestSessionRepository:
  def apply(connectionFactory: JdbcConnectionFactory): PostgresGuestSessionRepository =
    new PostgresGuestSessionRepository(connectionFactory)

final class PostgresClubRepository(
    protected val connectionFactory: JdbcConnectionFactory
) extends ClubRepository
    with JdbcRepositorySupport:
  override def save(club: Club): Club =
    try persist(club)
    catch
      case error: SQLException if PostgresErrors.isUniqueViolation(error, "idx_clubs_name") =>
        val normalized = findByName(club.name)
          .map(existing =>
            club.copy(
              id = existing.id,
              creator = existing.creator,
              createdAt = existing.createdAt,
              version = existing.version
            )
          )
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
        statement.setString(5, writeJson[Club](persisted))
        statement.setInt(6, club.version)
        statement.executeUpdate()
      }
    }

    requireOptimisticUpdate(
      rowsUpdated,
      "club",
      persisted.id.value,
      club.version,
      findById(persisted.id).map(_.version)
    )
    persisted

  override def findById(id: ClubId): Option[Club] =
    readOne[Club]("select payload from clubs where id = ?", { statement =>
      statement.setString(1, id.value)
    })

  override def findByName(name: String): Option[Club] =
    readOne[Club]("select payload from clubs where name = ?", { statement =>
      statement.setString(1, name)
    })

  override def findByIds(ids: Vector[ClubId]): Vector[Club] =
    if ids.isEmpty then Vector.empty
    else
      withConnection { connection =>
        Using.resource(
          connection.prepareStatement(
            """
              |select payload
              |from clubs
              |where id = any(?)
              |order by name asc
              |""".stripMargin
          )
        ) { statement =>
          statement.setArray(
            1,
            connection.createArrayOf("text", ids.map(_.value).distinct.toArray)
          )
          Using.resource(statement.executeQuery()) { resultSet =>
            val buffer = Vector.newBuilder[Club]
            while resultSet.next() do
              buffer += read[Club](resultSet.getString("payload"))
            buffer.result()
          }
        }
      }

  override def findFiltered(
      activeOnly: Boolean = false,
      joinableOnly: Boolean = false,
      memberId: Option[PlayerId] = None,
      adminId: Option[PlayerId] = None,
      name: Option[String] = None
  ): Vector[Club] =
    readAll[Club](
      """
        |select payload
        |from clubs
        |where (? = false or payload ->> 'dissolvedAt' is null)
        |  and (? = false or (
        |    payload ->> 'dissolvedAt' is null and
        |    coalesce((payload #>> '{recruitmentPolicy,applicationsOpen}')::boolean, false)
        |  ))
        |  and (? is null or payload @> cast(? as jsonb))
        |  and (? is null or payload @> cast(? as jsonb))
        |  and (? is null or lower(name) like ?)
        |order by name asc
        |""".stripMargin,
      { statement =>
        statement.setBoolean(1, activeOnly)
        statement.setBoolean(2, joinableOnly)
        setNullableString(statement, 3, memberId.map(_.value))
        setNullableString(statement, 4, memberId.map(id => s"""{"members":[{"value":"${id.value}"}]}"""))
        setNullableString(statement, 5, adminId.map(_.value))
        setNullableString(statement, 6, adminId.map(id => s"""{"admins":[{"value":"${id.value}"}]}"""))
        setNullableString(statement, 7, name)
        setNullableString(statement, 8, name.map(fragment => s"%${fragment.toLowerCase}%"))
      }
    )

  override def findAll(): Vector[Club] =
    readAll[Club]("select payload from clubs order by name")

object PostgresClubRepository:
  def apply(connectionFactory: JdbcConnectionFactory): PostgresClubRepository =
    new PostgresClubRepository(connectionFactory)

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
        statement.setString(1, s"""{"participatingClubs":[{"value":"${clubId.value}"}]}""")
        statement.setString(2, s"""{"whitelist":[{"clubId":{"value":"${clubId.value}"}}]}""")
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
        statement.setString(6, writeJson[Table](persisted))
        statement.setInt(7, table.version)
        statement.executeUpdate()
      }
    }

    requireOptimisticUpdate(
      rowsUpdated,
      "table",
      persisted.id.value,
      table.version,
      findById(persisted.id).map(_.version)
    )
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

  override def findByTournamentIds(tournamentIds: Vector[TournamentId]): Vector[Table] =
    if tournamentIds.isEmpty then Vector.empty
    else
      withConnection { connection =>
        Using.resource(
          connection.prepareStatement(
            """
              |select payload
              |from tables
              |where tournament_id = any(?)
              |order by tournament_id asc, stage_id asc, table_no asc
              |""".stripMargin
          )
        ) { statement =>
          statement.setArray(
            1,
            connection.createArrayOf("text", tournamentIds.map(_.value).toArray)
          )
          Using.resource(statement.executeQuery()) { resultSet =>
            val buffer = Vector.newBuilder[Table]
            while resultSet.next() do
              buffer += read[Table](resultSet.getString("payload"))
            buffer.result()
          }
        }
      }

  override def findAll(): Vector[Table] =
    readAll[Table]("select payload from tables order by updated_at desc")

object PostgresTableRepository:
  def apply(connectionFactory: JdbcConnectionFactory): PostgresTableRepository =
    new PostgresTableRepository(connectionFactory)

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
        statement.setString(7, writeJson[MatchRecord](record))
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

  override def findByTournamentAndStage(
      tournamentId: TournamentId,
      stageId: TournamentStageId
  ): Vector[MatchRecord] =
    readAll[MatchRecord](
      """
        |select payload
        |from match_records
        |where tournament_id = ? and stage_id = ?
        |order by generated_at desc, id desc
        |""".stripMargin,
      { statement =>
        statement.setString(1, tournamentId.value)
        statement.setString(2, stageId.value)
      }
    )

  override def findRecentByClub(clubId: ClubId, limit: Int): Vector[MatchRecord] =
    readAll[MatchRecord](
      """
        |select payload
        |from match_records
        |where exists (
        |  select 1
        |  from jsonb_array_elements(payload -> 'seatResults') as seat
        |  where seat ->> 'clubId' = ?
        |)
        |order by generated_at desc, id desc
        |limit ?
        |""".stripMargin,
      { statement =>
        statement.setString(1, clubId.value)
        statement.setInt(2, limit)
      }
    )

  override def findAll(): Vector[MatchRecord] =
    readAll[MatchRecord]("select payload from match_records order by generated_at desc")

object PostgresMatchRecordRepository:
  def apply(connectionFactory: JdbcConnectionFactory): PostgresMatchRecordRepository =
    new PostgresMatchRecordRepository(connectionFactory)

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
        statement.setString(7, writeJson[Paifu](paifu))
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

object PostgresPaifuRepository:
  def apply(connectionFactory: JdbcConnectionFactory): PostgresPaifuRepository =
    new PostgresPaifuRepository(connectionFactory)

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
        statement.setString(7, writeJson[AppealTicket](persisted))
        statement.setInt(8, ticket.version)
        statement.executeUpdate()
      }
    }

    requireOptimisticUpdate(
      rowsUpdated,
      "appeal-ticket",
      persisted.id.value,
      ticket.version,
      findById(persisted.id).map(_.version)
    )
    persisted

  override def findById(id: AppealTicketId): Option[AppealTicket] =
    readOne[AppealTicket]("select payload from appeal_tickets where id = ?", { statement =>
      statement.setString(1, id.value)
    })

  override def findAll(): Vector[AppealTicket] =
    readAll[AppealTicket]("select payload from appeal_tickets order by updated_at desc")

object PostgresAppealTicketRepository:
  def apply(connectionFactory: JdbcConnectionFactory): PostgresAppealTicketRepository =
    new PostgresAppealTicketRepository(connectionFactory)

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
        statement.setString(3, writeJson[Dashboard](persisted))
        statement.setInt(4, dashboard.version)
        statement.executeUpdate()
      }
    }

    requireOptimisticUpdate(
      rowsUpdated,
      "dashboard",
      ownerKey(persisted.owner),
      dashboard.version,
      findByOwner(persisted.owner).map(_.version)
    )
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

object PostgresDashboardRepository:
  def apply(connectionFactory: JdbcConnectionFactory): PostgresDashboardRepository =
    new PostgresDashboardRepository(connectionFactory)

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
        statement.setString(3, writeJson[AdvancedStatsBoard](persisted))
        statement.setInt(4, board.version)
        statement.executeUpdate()
      }
    }

    requireOptimisticUpdate(
      rowsUpdated,
      "advanced-stats-board",
      ownerKey(persisted.owner),
      board.version,
      findByOwner(persisted.owner).map(_.version)
    )
    persisted

  override def findByOwner(owner: DashboardOwner): Option[AdvancedStatsBoard] =
    readOne[AdvancedStatsBoard](
      "select payload from advanced_stats_boards where owner_key = ?",
      { statement =>
        statement.setString(1, ownerKey(owner))
      }
    )

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

object PostgresAdvancedStatsBoardRepository:
  def apply(connectionFactory: JdbcConnectionFactory): PostgresAdvancedStatsBoardRepository =
    new PostgresAdvancedStatsBoardRepository(connectionFactory)

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
        statement.setString(7, writeJson[AdvancedStatsRecomputeTask](persisted))
        statement.setInt(8, task.version)
        statement.executeUpdate()
      }
    }

    requireOptimisticUpdate(
      rowsUpdated,
      "advanced-stats-task",
      persisted.id.value,
      task.version,
      findById(persisted.id).map(_.version)
    )
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

  override def findPending(
      limit: Int,
      asOf: java.time.Instant = java.time.Instant.now()
  ): Vector[AdvancedStatsRecomputeTask] =
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

object PostgresAdvancedStatsRecomputeTaskRepository:
  def apply(
      connectionFactory: JdbcConnectionFactory
  ): PostgresAdvancedStatsRecomputeTaskRepository =
    new PostgresAdvancedStatsRecomputeTaskRepository(connectionFactory)

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
        statement.setString(3, writeJson[GlobalDictionaryEntry](persisted))
        statement.setInt(4, entry.version)
        statement.executeUpdate()
      }
    }

    requireOptimisticUpdate(
      rowsUpdated,
      "global-dictionary-entry",
      persisted.key,
      entry.version,
      findByKey(persisted.key).map(_.version)
    )
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

object PostgresGlobalDictionaryRepository:
  def apply(connectionFactory: JdbcConnectionFactory): PostgresGlobalDictionaryRepository =
    new PostgresGlobalDictionaryRepository(connectionFactory)

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
        statement.setString(5, writeJson[DictionaryNamespaceRegistration](persisted))
        statement.setInt(6, registration.version)
        statement.executeUpdate()
      }
    }

    requireOptimisticUpdate(
      rowsUpdated,
      "dictionary-namespace",
      persisted.namespacePrefix,
      registration.version,
      findByPrefix(persisted.namespacePrefix).map(_.version)
    )
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

object PostgresDictionaryNamespaceRepository:
  def apply(connectionFactory: JdbcConnectionFactory): PostgresDictionaryNamespaceRepository =
    new PostgresDictionaryNamespaceRepository(connectionFactory)

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
        statement.setString(5, writeJson[TournamentSettlementSnapshot](persisted))
        statement.setInt(6, snapshot.version)
        statement.executeUpdate()
      }
    }

    requireOptimisticUpdate(
      rowsUpdated,
      "tournament-settlement",
      persisted.id.value,
      snapshot.version,
      findById(persisted.id).map(_.version)
    )
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

object PostgresTournamentSettlementRepository:
  def apply(connectionFactory: JdbcConnectionFactory): PostgresTournamentSettlementRepository =
    new PostgresTournamentSettlementRepository(connectionFactory)

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
        statement.setString(7, writeJson[EventCascadeRecord](persisted))
        statement.setInt(8, record.version)
        statement.executeUpdate()
      }
    }

    requireOptimisticUpdate(
      rowsUpdated,
      "event-cascade-record",
      persisted.id.value,
      record.version,
      findById(persisted.id).map(_.version)
    )
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

object PostgresEventCascadeRecordRepository:
  def apply(connectionFactory: JdbcConnectionFactory): PostgresEventCascadeRecordRepository =
    new PostgresEventCascadeRecordRepository(connectionFactory)

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
        statement.setString(7, writeJson[AuditEventEntry](entry))
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

object PostgresAuditEventRepository:
  def apply(connectionFactory: JdbcConnectionFactory): PostgresAuditEventRepository =
    new PostgresAuditEventRepository(connectionFactory)

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
        statement.setString(9, writeJson[DomainEventOutboxRecord](persisted))
        statement.setInt(10, record.version)
        statement.executeUpdate()
      }
    }

    requireOptimisticUpdate(
      rowsUpdated,
      "domain-event-outbox-record",
      persisted.id.value,
      record.version,
      findById(persisted.id).map(_.version)
    )
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

  override def findPending(
      limit: Int,
      asOf: java.time.Instant = java.time.Instant.now()
  ): Vector[DomainEventOutboxRecord] =
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

object PostgresDomainEventOutboxRepository:
  def apply(connectionFactory: JdbcConnectionFactory): PostgresDomainEventOutboxRepository =
    new PostgresDomainEventOutboxRepository(connectionFactory)

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
        statement.setString(6, writeJson[DomainEventDeliveryReceipt](persisted))
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

object PostgresDomainEventDeliveryReceiptRepository:
  def apply(
      connectionFactory: JdbcConnectionFactory
  ): PostgresDomainEventDeliveryReceiptRepository =
    new PostgresDomainEventDeliveryReceiptRepository(connectionFactory)

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
        statement.setString(7, writeJson[DomainEventSubscriberCursor](persisted))
        statement.setInt(8, cursor.version)
        Using.resource(statement.executeQuery()) { resultSet =>
          if resultSet.next() then read[DomainEventSubscriberCursor](resultSet.getString("payload"))
          else
            throw OptimisticConcurrencyException(
              aggregateType = "domain-event-subscriber-cursor",
              aggregateId = s"${cursor.subscriberId}:${cursor.partitionKey}",
              expectedVersion = cursor.version,
              actualVersion =
                findBySubscriberAndPartition(cursor.subscriberId, cursor.partitionKey).map(_.version)
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

object PostgresDomainEventSubscriberCursorRepository:
  def apply(
      connectionFactory: JdbcConnectionFactory
  ): PostgresDomainEventSubscriberCursorRepository =
    new PostgresDomainEventSubscriberCursorRepository(connectionFactory)
