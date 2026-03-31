package database.postgres

import java.sql.Timestamp

import scala.util.Using

import ports.*
import riichinexus.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given

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
