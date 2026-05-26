package riichinexus.microservices.dictionary.tables.dictionarynamespace

import java.sql.Connection

object DictionaryNamespaceTableInitializer:
  private val initTableSql: String =
    """
      |create table if not exists dictionary_namespaces (
      |  namespace_prefix text primary key,
      |  owner_player_id text not null,
      |  status text not null,
      |  requested_at timestamptz not null,
      |  payload jsonb not null,
      |  updated_at timestamptz not null default now()
      |)
      |;
      |alter table dictionary_namespaces add column if not exists owner_player_id text;
      |alter table dictionary_namespaces add column if not exists status text;
      |alter table dictionary_namespaces add column if not exists requested_at timestamptz;
      |alter table dictionary_namespaces add column if not exists payload jsonb;
      |alter table dictionary_namespaces add column if not exists updated_at timestamptz default now();
      |create index if not exists idx_dictionary_namespaces_owner on dictionary_namespaces (owner_player_id);
      |create index if not exists idx_dictionary_namespaces_status on dictionary_namespaces (status, requested_at);
      |""".stripMargin

  private[riichinexus] def initialize(connection: Connection): Unit =
    val statement = connection.createStatement()
    try statement.execute(initTableSql)
    finally statement.close()
