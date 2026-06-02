package riichinexus.microservices.tournament.mahjongcore.tables.tablestate

import java.sql.Connection

object MahjongTableStateTableInitializer:
  private val initTableSql: String =
    """
      |create table if not exists mahjong_table_states (
      |  table_id text primary key,
      |  status text not null,
      |  version integer not null,
      |  payload jsonb not null,
      |  archived_paifu_id text,
      |  archived_match_record_id text,
      |  updated_at timestamptz not null default now()
      |)
      |;
      |alter table mahjong_table_states add column if not exists table_id text;
      |alter table mahjong_table_states add column if not exists status text;
      |alter table mahjong_table_states add column if not exists version integer;
      |alter table mahjong_table_states add column if not exists payload jsonb;
      |alter table mahjong_table_states add column if not exists archived_paifu_id text;
      |alter table mahjong_table_states add column if not exists archived_match_record_id text;
      |alter table mahjong_table_states add column if not exists updated_at timestamptz default now();
      |create index if not exists idx_mahjong_table_states_status on mahjong_table_states (status);
      |create index if not exists idx_mahjong_table_states_updated_at on mahjong_table_states (updated_at);
      |""".stripMargin

  private[riichinexus] def initialize(connection: Connection): Unit =
    val statement = connection.createStatement()
    try statement.execute(initTableSql)
    finally statement.close()
