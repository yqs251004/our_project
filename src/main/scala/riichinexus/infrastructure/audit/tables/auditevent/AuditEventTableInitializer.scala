package riichinexus.infrastructure.audit.tables.auditevent

import java.sql.Connection

object AuditEventTableInitializer:
  private val initTableSql: String =
    """
      |create table if not exists audit_events (
      |  id text primary key,
      |  aggregate_type text not null,
      |  aggregate_id text not null,
      |  event_type text not null,
      |  occurred_at timestamptz not null,
      |  actor_id text null,
      |  payload jsonb not null
      |);
      |alter table audit_events add column if not exists aggregate_type text;
      |alter table audit_events add column if not exists aggregate_id text;
      |alter table audit_events add column if not exists event_type text;
      |alter table audit_events add column if not exists occurred_at timestamptz;
      |alter table audit_events add column if not exists actor_id text;
      |alter table audit_events add column if not exists payload jsonb;
      |create index if not exists idx_audit_events_aggregate on audit_events (aggregate_type, aggregate_id);
      |create index if not exists idx_audit_events_occurred_at on audit_events (occurred_at);
      |""".stripMargin

  private[riichinexus] def initialize(connection: Connection): Unit =
    val statement = connection.createStatement()
    try statement.execute(initTableSql)
    finally statement.close()
