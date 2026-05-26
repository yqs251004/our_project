package riichinexus.infrastructure.events.tables.domaineventdeliveryreceipt

import java.sql.Connection

object DomainEventDeliveryReceiptTableInitializer:
  private val initTableSql: String =
    """
      |create table if not exists domain_event_delivery_receipts (
      |  id text primary key,
      |  outbox_record_id text not null,
      |  subscriber_id text not null,
      |  event_type text not null,
      |  delivered_at timestamptz not null,
      |  payload jsonb not null,
      |  updated_at timestamptz not null default now()
      |);
      |alter table domain_event_delivery_receipts add column if not exists outbox_record_id text;
      |alter table domain_event_delivery_receipts add column if not exists subscriber_id text;
      |alter table domain_event_delivery_receipts add column if not exists event_type text;
      |alter table domain_event_delivery_receipts add column if not exists delivered_at timestamptz;
      |alter table domain_event_delivery_receipts add column if not exists payload jsonb;
      |alter table domain_event_delivery_receipts add column if not exists updated_at timestamptz default now();
      |create unique index if not exists idx_domain_event_delivery_receipts_unique on domain_event_delivery_receipts (outbox_record_id, subscriber_id);
      |create index if not exists idx_domain_event_delivery_receipts_outbox on domain_event_delivery_receipts (outbox_record_id, delivered_at);
      |""".stripMargin

  private[riichinexus] def initialize(connection: Connection): Unit =
    val statement = connection.createStatement()
    try statement.execute(initTableSql)
    finally statement.close()
