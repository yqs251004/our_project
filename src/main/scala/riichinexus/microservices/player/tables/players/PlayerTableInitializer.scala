package riichinexus.microservices.player.tables.players

import java.sql.Connection

object PlayerTableInitializer:
  private val initTableSql: String =
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
      |;
      |alter table players add column if not exists user_id text;
      |alter table players add column if not exists nickname text;
      |alter table players add column if not exists club_id text;
      |alter table players add column if not exists elo integer;
      |alter table players add column if not exists payload jsonb;
      |alter table players add column if not exists updated_at timestamptz default now();
      |create unique index if not exists idx_players_user_id on players (user_id);
      |create index if not exists idx_players_club_id on players (club_id);
      |""".stripMargin

  private[riichinexus] def initialize(connection: Connection): Unit =
    val statement = connection.createStatement()
    try statement.execute(initTableSql)
    finally statement.close()
