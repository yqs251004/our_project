package riichinexus.microservices.auth.tables.guestsession

import java.sql.Connection


object GuestSessionTableInitializer:
  private val initTableSql: String =
    """
      |create table if not exists guest_sessions (
      |  id text primary key,
      |  created_at timestamptz not null,
      |  display_name text not null,
      |  payload jsonb not null,
      |  updated_at timestamptz not null default now()
      |)
      |;
      |alter table guest_sessions add column if not exists created_at timestamptz;
      |alter table guest_sessions add column if not exists display_name text;
      |alter table guest_sessions add column if not exists payload jsonb;
      |alter table guest_sessions add column if not exists updated_at timestamptz default now();
      |create index if not exists idx_guest_sessions_created_at on guest_sessions (created_at desc);
      |""".stripMargin

  private[riichinexus] def initialize(connection: Connection): Unit =
    val statement = connection.createStatement()
    try statement.execute(initTableSql)
    finally statement.close()
