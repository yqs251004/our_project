package riichinexus.infrastructure.events.tables.domaineventoutbox

import java.sql.Connection

object DomainEventOutboxTableInitializer:
  private val initTableSql: String =
    """
      |create sequence if not exists domain_event_outbox_sequence start 1;
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
      |);
      |alter table domain_event_outbox add column if not exists sequence_no bigint;
      |alter table domain_event_outbox add column if not exists event_type text;
      |alter table domain_event_outbox add column if not exists aggregate_type text;
      |alter table domain_event_outbox add column if not exists aggregate_id text;
      |alter table domain_event_outbox add column if not exists status text;
      |alter table domain_event_outbox add column if not exists occurred_at timestamptz;
      |alter table domain_event_outbox add column if not exists available_at timestamptz;
      |alter table domain_event_outbox add column if not exists payload jsonb;
      |alter table domain_event_outbox add column if not exists updated_at timestamptz default now();
      |update domain_event_outbox
      |set sequence_no = numbered.sequence_no
      |from (
      |  select id, row_number() over(order by occurred_at asc, id asc) as sequence_no
      |  from domain_event_outbox
      |) as numbered
      |where domain_event_outbox.id = numbered.id
      |  and domain_event_outbox.sequence_no is null;
      |update domain_event_outbox set aggregate_type = coalesce(aggregate_type, 'domain-event');
      |update domain_event_outbox set aggregate_id = coalesce(aggregate_id, id);
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
      |   or not (payload ? 'aggregateId');
      |create unique index if not exists idx_domain_event_outbox_sequence on domain_event_outbox (sequence_no);
      |create index if not exists idx_domain_event_outbox_status on domain_event_outbox (status, available_at, sequence_no);
      |create index if not exists idx_domain_event_outbox_occurred_at on domain_event_outbox (occurred_at);
      |create index if not exists idx_domain_event_outbox_aggregate on domain_event_outbox (aggregate_type, aggregate_id, sequence_no);
      |select case
      |  when exists(select 1 from domain_event_outbox)
      |    then setval('domain_event_outbox_sequence', (select max(sequence_no) from domain_event_outbox), true)
      |  else setval('domain_event_outbox_sequence', 1, false)
      |end;
      |""".stripMargin

  private[riichinexus] def initialize(connection: Connection): Unit =
    val statement = connection.createStatement()
    try statement.execute(initTableSql)
    finally statement.close()
