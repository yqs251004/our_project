package riichinexus.microservices.auth.tables.accountcredential

import java.sql.Connection


object AccountCredentialTableInitializer:
  private val initTableSql: String =
    """
      |create table if not exists account_credentials (
      |  username text primary key,
      |  player_id text not null,
      |  payload jsonb not null,
      |  updated_at timestamptz not null default now()
      |)
      |;
      |alter table account_credentials add column if not exists player_id text;
      |alter table account_credentials add column if not exists payload jsonb;
      |alter table account_credentials add column if not exists updated_at timestamptz default now();
      |create unique index if not exists idx_account_credentials_player_id on account_credentials (player_id);
      |""".stripMargin

  private[riichinexus] def initialize(connection: Connection): Unit =
    val statement = connection.createStatement()
    try statement.execute(initTableSql)
    finally statement.close()
