package riichinexus.infrastructure.events.tables.domaineventsubscribercursor

import java.sql.Connection

object DomainEventSubscriberCursorTableInitializer:
  private val initTableSql: String =
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
      |);
      |alter table domain_event_subscriber_cursors add column if not exists subscriber_id text;
      |alter table domain_event_subscriber_cursors add column if not exists partition_key text;
      |alter table domain_event_subscriber_cursors add column if not exists last_outbox_record_id text;
      |alter table domain_event_subscriber_cursors add column if not exists last_sequence_no bigint;
      |alter table domain_event_subscriber_cursors add column if not exists advanced_at timestamptz;
      |alter table domain_event_subscriber_cursors add column if not exists payload jsonb;
      |alter table domain_event_subscriber_cursors add column if not exists updated_at timestamptz default now();
      |create unique index if not exists idx_domain_event_subscriber_cursors_unique on domain_event_subscriber_cursors (subscriber_id, partition_key);
      |create index if not exists idx_domain_event_subscriber_cursors_advanced_at on domain_event_subscriber_cursors (advanced_at);
      |""".stripMargin

  private[riichinexus] def initialize(connection: Connection): Unit =
    val statement = connection.createStatement()
    try statement.execute(initTableSql)
    finally statement.close()
