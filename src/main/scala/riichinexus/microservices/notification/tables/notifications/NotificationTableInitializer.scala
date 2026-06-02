package riichinexus.microservices.notification.tables.notifications

import java.sql.Connection

object NotificationTableInitializer:
  private val initTableSql: String =
    """
      |create table if not exists notifications (
      |  id text primary key,
      |  recipient_player_id text not null,
      |  notification_type text not null,
      |  source_service text not null,
      |  source_type text not null,
      |  source_id text not null,
      |  created_at timestamptz not null,
      |  read_at timestamptz null,
      |  expires_at timestamptz null,
      |  payload jsonb not null
      |);
      |alter table notifications add column if not exists recipient_player_id text;
      |alter table notifications add column if not exists notification_type text;
      |alter table notifications add column if not exists source_service text;
      |alter table notifications add column if not exists source_type text;
      |alter table notifications add column if not exists source_id text;
      |alter table notifications add column if not exists created_at timestamptz;
      |alter table notifications add column if not exists read_at timestamptz;
      |alter table notifications add column if not exists expires_at timestamptz;
      |alter table notifications add column if not exists payload jsonb;
      |create index if not exists idx_notifications_recipient_created_at on notifications (recipient_player_id, created_at desc);
      |create index if not exists idx_notifications_recipient_unread on notifications (recipient_player_id, read_at) where read_at is null;
      |""".stripMargin

  private[riichinexus] def initialize(connection: Connection): Unit =
    val statement = connection.createStatement()
    try statement.execute(initTableSql)
    finally statement.close()
