package riichinexus.infrastructure.events.tables.eventcascaderecord

import java.sql.Connection

object EventCascadeRecordTableInitializer:
  private val initTableSql: String =
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
      |;
      |alter table event_cascade_records add column if not exists consumer text;
      |alter table event_cascade_records add column if not exists status text;
      |alter table event_cascade_records add column if not exists aggregate_type text;
      |alter table event_cascade_records add column if not exists aggregate_id text;
      |alter table event_cascade_records add column if not exists occurred_at timestamptz;
      |alter table event_cascade_records add column if not exists payload jsonb;
      |alter table event_cascade_records add column if not exists updated_at timestamptz default now();
      |create index if not exists idx_event_cascade_records_status on event_cascade_records (status, occurred_at);
      |create index if not exists idx_event_cascade_records_consumer on event_cascade_records (consumer, occurred_at);
      |create index if not exists idx_event_cascade_records_aggregate on event_cascade_records (aggregate_type, aggregate_id);
      |""".stripMargin

  private[riichinexus] def initialize(connection: Connection): Unit =
    val statement = connection.createStatement()
    try statement.execute(initTableSql)
    finally statement.close()
