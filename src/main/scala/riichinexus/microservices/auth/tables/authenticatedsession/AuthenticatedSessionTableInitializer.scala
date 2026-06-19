package riichinexus.microservices.auth.tables.authenticatedsession

import java.sql.Connection


object AuthenticatedSessionTableInitializer:
  private val initTableSql: String =
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
      |;
      |alter table authenticated_sessions add column if not exists username text;
      |alter table authenticated_sessions add column if not exists player_id text;
      |alter table authenticated_sessions add column if not exists created_at timestamptz;
      |alter table authenticated_sessions add column if not exists expires_at timestamptz;
      |alter table authenticated_sessions add column if not exists payload jsonb;
      |alter table authenticated_sessions add column if not exists updated_at timestamptz default now();
      |create index if not exists idx_authenticated_sessions_player_id on authenticated_sessions (player_id);
      |create index if not exists idx_authenticated_sessions_username on authenticated_sessions (username);
      |create index if not exists idx_authenticated_sessions_expires_at on authenticated_sessions (expires_at);
      |""".stripMargin

  private[riichinexus] def initialize(connection: Connection): Unit =
    val statement = connection.createStatement()
    try statement.execute(initTableSql)
    finally statement.close()
