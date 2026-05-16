package riichinexus.infrastructure.postgres

private[postgres] object PostgresSchemaDefinitions:
  private final case class TableSchema(
      createSql: String,
      migrations: Vector[String] = Vector.empty,
      indexes: Vector[String] = Vector.empty
  ):
    def statements: Vector[String] =
      Vector(createSql) ++ migrations ++ indexes

  private val coreSchema: Vector[String] =
    Vector(
      TableSchema(
        createSql =
          """
            |create table if not exists schema_version (
            |  version integer primary key,
            |  description text not null,
            |  applied_at timestamptz not null default now()
            |)
            |""".stripMargin
      ),
      TableSchema(
        createSql =
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
            |""".stripMargin,
        migrations = Vector(
          "alter table players add column if not exists user_id text",
          "alter table players add column if not exists nickname text",
          "alter table players add column if not exists club_id text",
          "alter table players add column if not exists elo integer",
          "alter table players add column if not exists payload jsonb",
          "alter table players add column if not exists updated_at timestamptz default now()"
        ),
        indexes = Vector(
          "create unique index if not exists idx_players_user_id on players (user_id)",
          "create index if not exists idx_players_club_id on players (club_id)"
        )
      ),
      TableSchema(
        createSql =
          """
            |create table if not exists clubs (
            |  id text primary key,
            |  name text not null,
            |  creator_id text not null,
            |  total_points integer not null,
            |  payload jsonb not null,
            |  updated_at timestamptz not null default now()
            |)
            |""".stripMargin,
        migrations = Vector(
          "alter table clubs add column if not exists name text",
          "alter table clubs add column if not exists creator_id text",
          "alter table clubs add column if not exists total_points integer",
          "alter table clubs add column if not exists payload jsonb",
          "alter table clubs add column if not exists updated_at timestamptz default now()"
        ),
        indexes = Vector(
          "create unique index if not exists idx_clubs_name on clubs (name)",
          "create index if not exists idx_clubs_payload_gin on clubs using gin (payload)"
        )
      )
    ).flatMap(_.statements)

  private val authSchema: Vector[String] =
    Vector(
      TableSchema(
        createSql =
          """
            |create table if not exists guest_sessions (
            |  id text primary key,
            |  created_at timestamptz not null,
            |  display_name text not null,
            |  payload jsonb not null,
            |  updated_at timestamptz not null default now()
            |)
            |""".stripMargin,
        migrations = Vector(
          "alter table guest_sessions add column if not exists created_at timestamptz",
          "alter table guest_sessions add column if not exists display_name text",
          "alter table guest_sessions add column if not exists payload jsonb",
          "alter table guest_sessions add column if not exists updated_at timestamptz default now()"
        ),
        indexes = Vector("create index if not exists idx_guest_sessions_created_at on guest_sessions (created_at desc)")
      ),
      TableSchema(
        createSql =
          """
            |create table if not exists account_credentials (
            |  username text primary key,
            |  player_id text not null,
            |  payload jsonb not null,
            |  updated_at timestamptz not null default now()
            |)
            |""".stripMargin,
        migrations = Vector(
          "alter table account_credentials add column if not exists player_id text",
          "alter table account_credentials add column if not exists payload jsonb",
          "alter table account_credentials add column if not exists updated_at timestamptz default now()"
        ),
        indexes = Vector(
          "create unique index if not exists idx_account_credentials_player_id on account_credentials (player_id)"
        )
      ),
      TableSchema(
        createSql =
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
            |""".stripMargin,
        migrations = Vector(
          "alter table authenticated_sessions add column if not exists username text",
          "alter table authenticated_sessions add column if not exists player_id text",
          "alter table authenticated_sessions add column if not exists created_at timestamptz",
          "alter table authenticated_sessions add column if not exists expires_at timestamptz",
          "alter table authenticated_sessions add column if not exists payload jsonb",
          "alter table authenticated_sessions add column if not exists updated_at timestamptz default now()"
        ),
        indexes = Vector(
          "create index if not exists idx_authenticated_sessions_player_id on authenticated_sessions (player_id)",
          "create index if not exists idx_authenticated_sessions_username on authenticated_sessions (username)",
          "create index if not exists idx_authenticated_sessions_expires_at on authenticated_sessions (expires_at)"
        )
      )
    ).flatMap(_.statements)

  private val tournamentSchema: Vector[String] =
    Vector(
      TableSchema(
        createSql =
          """
            |create table if not exists tournaments (
            |  id text primary key,
            |  name text not null,
            |  organizer text not null,
            |  status text not null,
            |  payload jsonb not null,
            |  updated_at timestamptz not null default now()
            |)
            |""".stripMargin,
        migrations = Vector(
          "alter table tournaments add column if not exists name text",
          "alter table tournaments add column if not exists organizer text",
          "alter table tournaments add column if not exists status text",
          "alter table tournaments add column if not exists payload jsonb",
          "alter table tournaments add column if not exists updated_at timestamptz default now()"
        ),
        indexes = Vector(
          "create unique index if not exists idx_tournaments_name_start on tournaments (name, organizer)",
          "create index if not exists idx_tournaments_payload_gin on tournaments using gin (payload)"
        )
      ),
      TableSchema(
        createSql =
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
            |""".stripMargin,
        migrations = Vector(
          "alter table tables add column if not exists tournament_id text",
          "alter table tables add column if not exists stage_id text",
          "alter table tables add column if not exists table_no integer",
          "alter table tables add column if not exists status text",
          "alter table tables add column if not exists payload jsonb",
          "alter table tables add column if not exists updated_at timestamptz default now()"
        ),
        indexes = Vector(
          "create unique index if not exists idx_tables_stage_table_no on tables (tournament_id, stage_id, table_no)",
          "create index if not exists idx_tables_tournament_stage on tables (tournament_id, stage_id)"
        )
      ),
      TableSchema(
        createSql =
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
            |""".stripMargin,
        migrations = Vector(
          "alter table paifus add column if not exists table_id text",
          "alter table paifus add column if not exists tournament_id text",
          "alter table paifus add column if not exists stage_id text",
          "alter table paifus add column if not exists recorded_at timestamptz",
          "alter table paifus add column if not exists player_ids text[]",
          "alter table paifus add column if not exists payload jsonb",
          "alter table paifus add column if not exists updated_at timestamptz default now()"
        ),
        indexes = Vector(
          "create index if not exists idx_paifus_table_id on paifus (table_id)",
          "create index if not exists idx_paifus_recorded_at on paifus (recorded_at)",
          "create index if not exists idx_paifus_player_ids on paifus using gin (player_ids)"
        )
      ),
      TableSchema(
        createSql =
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
            |""".stripMargin,
        migrations = Vector(
          "alter table match_records add column if not exists table_id text",
          "alter table match_records add column if not exists tournament_id text",
          "alter table match_records add column if not exists stage_id text",
          "alter table match_records add column if not exists generated_at timestamptz",
          "alter table match_records add column if not exists player_ids text[]",
          "alter table match_records add column if not exists payload jsonb",
          "alter table match_records add column if not exists updated_at timestamptz default now()"
        ),
        indexes = Vector(
          "create unique index if not exists idx_match_records_table_id on match_records (table_id)",
          "create index if not exists idx_match_records_tournament_id on match_records (tournament_id)",
          "create index if not exists idx_match_records_tournament_stage_generated_at on match_records (tournament_id, stage_id, generated_at desc)",
          "create index if not exists idx_match_records_player_ids on match_records using gin (player_ids)"
        )
      ),
      TableSchema(
        createSql =
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
            |""".stripMargin,
        migrations = Vector(
          "alter table appeal_tickets add column if not exists table_id text",
          "alter table appeal_tickets add column if not exists tournament_id text",
          "alter table appeal_tickets add column if not exists stage_id text",
          "alter table appeal_tickets add column if not exists status text",
          "alter table appeal_tickets add column if not exists opened_by text",
          "alter table appeal_tickets add column if not exists payload jsonb",
          "alter table appeal_tickets add column if not exists updated_at timestamptz default now()"
        ),
        indexes = Vector(
          "create index if not exists idx_appeals_tournament_id on appeal_tickets (tournament_id)",
          "create index if not exists idx_appeals_table_id on appeal_tickets (table_id)"
        )
      ),
      TableSchema(
        createSql =
          """
            |create table if not exists tournament_settlements (
            |  id text primary key,
            |  tournament_id text not null,
            |  stage_id text not null,
            |  generated_at timestamptz not null,
            |  payload jsonb not null,
            |  updated_at timestamptz not null default now()
            |)
            |""".stripMargin,
        migrations = Vector(
          "alter table tournament_settlements add column if not exists tournament_id text",
          "alter table tournament_settlements add column if not exists stage_id text",
          "alter table tournament_settlements add column if not exists generated_at timestamptz",
          "alter table tournament_settlements add column if not exists payload jsonb",
          "alter table tournament_settlements add column if not exists updated_at timestamptz default now()"
        ),
        indexes = Vector(
          "create unique index if not exists idx_tournament_settlements_scope on tournament_settlements (tournament_id, stage_id)",
          "create index if not exists idx_tournament_settlements_generated_at on tournament_settlements (generated_at)"
        )
      )
    ).flatMap(_.statements)

  private val analyticsSchema: Vector[String] =
    Vector(
      TableSchema(
        createSql =
          """
            |create table if not exists dashboards (
            |  owner_key text primary key,
            |  owner_type text not null,
            |  payload jsonb not null,
            |  updated_at timestamptz not null default now()
            |)
            |""".stripMargin,
        migrations = Vector(
          "alter table dashboards add column if not exists owner_type text",
          "alter table dashboards add column if not exists payload jsonb",
          "alter table dashboards add column if not exists updated_at timestamptz default now()"
        )
      ),
      TableSchema(
        createSql =
          """
            |create table if not exists advanced_stats_boards (
            |  owner_key text primary key,
            |  owner_type text not null,
            |  payload jsonb not null,
            |  updated_at timestamptz not null default now()
            |)
            |""".stripMargin,
        migrations = Vector(
          "alter table advanced_stats_boards add column if not exists owner_type text",
          "alter table advanced_stats_boards add column if not exists payload jsonb",
          "alter table advanced_stats_boards add column if not exists updated_at timestamptz default now()"
        )
      ),
      TableSchema(
        createSql =
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
            |""".stripMargin,
        migrations = Vector(
          "alter table advanced_stats_recompute_tasks add column if not exists owner_key text",
          "alter table advanced_stats_recompute_tasks add column if not exists owner_type text",
          "alter table advanced_stats_recompute_tasks add column if not exists status text",
          "alter table advanced_stats_recompute_tasks add column if not exists calculator_version integer",
          "alter table advanced_stats_recompute_tasks add column if not exists requested_at timestamptz",
          "alter table advanced_stats_recompute_tasks add column if not exists payload jsonb",
          "alter table advanced_stats_recompute_tasks add column if not exists updated_at timestamptz default now()"
        ),
        indexes = Vector(
          "create index if not exists idx_advanced_stats_tasks_pending on advanced_stats_recompute_tasks (status, requested_at)",
          "create index if not exists idx_advanced_stats_tasks_owner on advanced_stats_recompute_tasks (owner_key, calculator_version, status)"
        )
      )
    ).flatMap(_.statements)

  private val dictionarySchema: Vector[String] =
    Vector(
      TableSchema(
        createSql =
          """
            |create table if not exists global_dictionary (
            |  key text primary key,
            |  updated_at timestamptz not null,
            |  payload jsonb not null
            |)
            |""".stripMargin,
        migrations = Vector(
          "alter table global_dictionary add column if not exists updated_at timestamptz",
          "alter table global_dictionary add column if not exists payload jsonb"
        )
      )
    ).flatMap(_.statements)

  private val eventSchema: Vector[String] =
    Vector(
      TableSchema(
        createSql =
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
            |""".stripMargin,
        migrations = Vector(
          "alter table event_cascade_records add column if not exists consumer text",
          "alter table event_cascade_records add column if not exists status text",
          "alter table event_cascade_records add column if not exists aggregate_type text",
          "alter table event_cascade_records add column if not exists aggregate_id text",
          "alter table event_cascade_records add column if not exists occurred_at timestamptz",
          "alter table event_cascade_records add column if not exists payload jsonb",
          "alter table event_cascade_records add column if not exists updated_at timestamptz default now()"
        ),
        indexes = Vector(
          "create index if not exists idx_event_cascade_records_status on event_cascade_records (status, occurred_at)",
          "create index if not exists idx_event_cascade_records_consumer on event_cascade_records (consumer, occurred_at)",
          "create index if not exists idx_event_cascade_records_aggregate on event_cascade_records (aggregate_type, aggregate_id)"
        )
      ),
      TableSchema(
        createSql =
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
            |""".stripMargin,
        migrations = Vector(
          "create sequence if not exists domain_event_outbox_sequence start 1",
          "alter table domain_event_outbox add column if not exists sequence_no bigint",
          "alter table domain_event_outbox add column if not exists event_type text",
          "alter table domain_event_outbox add column if not exists aggregate_type text",
          "alter table domain_event_outbox add column if not exists aggregate_id text",
          "alter table domain_event_outbox add column if not exists status text",
          "alter table domain_event_outbox add column if not exists occurred_at timestamptz",
          "alter table domain_event_outbox add column if not exists available_at timestamptz",
          "alter table domain_event_outbox add column if not exists payload jsonb",
          "alter table domain_event_outbox add column if not exists updated_at timestamptz default now()",
          """
            |update domain_event_outbox
            |set sequence_no = numbered.sequence_no
            |from (
            |  select id, row_number() over(order by occurred_at asc, id asc) as sequence_no
            |  from domain_event_outbox
            |) as numbered
            |where domain_event_outbox.id = numbered.id
            |  and domain_event_outbox.sequence_no is null
            |""".stripMargin,
          "update domain_event_outbox set aggregate_type = coalesce(aggregate_type, 'domain-event')",
          "update domain_event_outbox set aggregate_id = coalesce(aggregate_id, id)",
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
        ),
        indexes = Vector(
          "create unique index if not exists idx_domain_event_outbox_sequence on domain_event_outbox (sequence_no)",
          "create index if not exists idx_domain_event_outbox_status on domain_event_outbox (status, available_at, sequence_no)",
          "create index if not exists idx_domain_event_outbox_occurred_at on domain_event_outbox (occurred_at)",
          "create index if not exists idx_domain_event_outbox_aggregate on domain_event_outbox (aggregate_type, aggregate_id, sequence_no)",
          """
            |select case
            |  when exists(select 1 from domain_event_outbox)
            |    then setval('domain_event_outbox_sequence', (select max(sequence_no) from domain_event_outbox), true)
            |  else setval('domain_event_outbox_sequence', 1, false)
            |end
            |""".stripMargin
        )
      ),
      TableSchema(
        createSql =
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
            |""".stripMargin,
        migrations = Vector(
          "alter table domain_event_delivery_receipts add column if not exists outbox_record_id text",
          "alter table domain_event_delivery_receipts add column if not exists subscriber_id text",
          "alter table domain_event_delivery_receipts add column if not exists event_type text",
          "alter table domain_event_delivery_receipts add column if not exists delivered_at timestamptz",
          "alter table domain_event_delivery_receipts add column if not exists payload jsonb",
          "alter table domain_event_delivery_receipts add column if not exists updated_at timestamptz default now()"
        ),
        indexes = Vector(
          "create unique index if not exists idx_domain_event_delivery_receipts_unique on domain_event_delivery_receipts (outbox_record_id, subscriber_id)",
          "create index if not exists idx_domain_event_delivery_receipts_outbox on domain_event_delivery_receipts (outbox_record_id, delivered_at)"
        )
      ),
      TableSchema(
        createSql =
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
            |""".stripMargin,
        migrations = Vector(
          "alter table domain_event_subscriber_cursors add column if not exists subscriber_id text",
          "alter table domain_event_subscriber_cursors add column if not exists partition_key text",
          "alter table domain_event_subscriber_cursors add column if not exists last_outbox_record_id text",
          "alter table domain_event_subscriber_cursors add column if not exists last_sequence_no bigint",
          "alter table domain_event_subscriber_cursors add column if not exists advanced_at timestamptz",
          "alter table domain_event_subscriber_cursors add column if not exists payload jsonb",
          "alter table domain_event_subscriber_cursors add column if not exists updated_at timestamptz default now()"
        ),
        indexes = Vector(
          "create unique index if not exists idx_domain_event_subscriber_cursors_unique on domain_event_subscriber_cursors (subscriber_id, partition_key)",
          "create index if not exists idx_domain_event_subscriber_cursors_advanced_at on domain_event_subscriber_cursors (advanced_at)"
        )
      )
    ).flatMap(_.statements)

  private val auditSchema: Vector[String] =
    Vector(
      TableSchema(
        createSql =
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
            |""".stripMargin,
        migrations = Vector(
          "alter table audit_events add column if not exists aggregate_type text",
          "alter table audit_events add column if not exists aggregate_id text",
          "alter table audit_events add column if not exists event_type text",
          "alter table audit_events add column if not exists occurred_at timestamptz",
          "alter table audit_events add column if not exists actor_id text",
          "alter table audit_events add column if not exists payload jsonb"
        ),
        indexes = Vector(
          "create index if not exists idx_audit_events_aggregate on audit_events (aggregate_type, aggregate_id)",
          "create index if not exists idx_audit_events_occurred_at on audit_events (occurred_at)"
        )
      )
    ).flatMap(_.statements)

  private val schemaVersionMarkers: Vector[String] =
    Vector(
      1 -> "Initial RiichiNexus PostgreSQL schema",
      2 -> "Extended RiichiNexus tournament workflow schema",
      3 -> "Added settlement persistence and audit event schema",
      4 -> "Added guest session persistence schema",
      5 -> "Added advanced stats board persistence schema",
      6 -> "Added advanced stats recompute task pipeline schema",
      7 -> "Added event cascade record subscriber schema",
      8 -> "Added dictionary namespace governance schema",
      9 -> "Added durable domain event outbox schema",
      10 -> "Added domain event delivery receipt schema",
      11 -> "Added subscriber ordering cursor and partitioned outbox schema",
      12 -> "Added account credential and authenticated session schema"
    ).map { case (version, description) =>
      s"""
         |insert into schema_version(version, description)
         |values ($version, '$description')
         |on conflict (version) do nothing
         |""".stripMargin
    }

  val statements: Vector[String] =
    Vector(
      coreSchema,
      authSchema,
      tournamentSchema,
      analyticsSchema,
      dictionarySchema,
      eventSchema,
      auditSchema,
      schemaVersionMarkers
    ).flatten
