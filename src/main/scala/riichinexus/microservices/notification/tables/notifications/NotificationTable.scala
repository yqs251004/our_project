package riichinexus.microservices.notification.tables.notifications

import java.sql.{Connection, PreparedStatement, ResultSet, Timestamp, Types}
import java.time.Instant

import scala.annotation.tailrec
import scala.util.Using

import riichinexus.microservices.notification.objects.Notification
import riichinexus.microservices.player.objects.PlayerId

import upickle.default.{read, write}


object NotificationTable:
  private val upsertSql: String =
    """
      |insert into notifications (
      |  id,
      |  recipient_player_id,
      |  notification_type,
      |  source_service,
      |  source_type,
      |  source_id,
      |  created_at,
      |  read_at,
      |  expires_at,
      |  payload
      |)
      |values (?, ?, ?, ?, ?, ?, ?, ?, ?, cast(? as jsonb))
      |on conflict (id) do update set
      |  recipient_player_id = excluded.recipient_player_id,
      |  notification_type = excluded.notification_type,
      |  source_service = excluded.source_service,
      |  source_type = excluded.source_type,
      |  source_id = excluded.source_id,
      |  created_at = excluded.created_at,
      |  read_at = excluded.read_at,
      |  expires_at = excluded.expires_at,
      |  payload = excluded.payload
      |""".stripMargin

  private[notification] def save(connection: Connection, notification: Notification): Notification =
    Using.resource(connection.prepareStatement(upsertSql)) { statement =>
      statement.setString(1, notification.id.value)
      statement.setString(2, notification.recipientPlayerId.value)
      statement.setString(3, notification.notificationType.toString)
      statement.setString(4, notification.sourceService)
      statement.setString(5, notification.sourceType)
      statement.setString(6, notification.sourceId)
      statement.setTimestamp(7, Timestamp.from(notification.createdAt))
      setNullableInstant(statement, 8, notification.readAt)
      setNullableInstant(statement, 9, notification.expiresAt)
      statement.setString(10, write[Notification](notification))
      statement.executeUpdate()
    }
    notification

  private val findByIdSql: String =
    """
      |select payload
      |from notifications
      |where id = ? and recipient_player_id = ?
      |limit 1
      |""".stripMargin

  private[notification] def findById(
      connection: Connection,
      notificationId: String,
      recipientPlayerId: PlayerId
  ): Option[Notification] =
    Using.resource(connection.prepareStatement(findByIdSql)) { statement =>
      statement.setString(1, notificationId)
      statement.setString(2, recipientPlayerId.value)
      Using.resource(statement.executeQuery()) { resultSet =>
        if resultSet.next() then Some(readNotification(resultSet))
        else None
      }
    }

  private val listForRecipientSql: String =
    """
      |select payload
      |from notifications
      |where recipient_player_id = ?
      |  and (? = false or read_at is null)
      |  and (expires_at is null or expires_at > now())
      |order by created_at desc, id desc
      |limit ?
      |offset ?
      |""".stripMargin

  private[notification] def listForRecipient(
      connection: Connection,
      recipientPlayerId: PlayerId,
      unreadOnly: Boolean,
      limit: Int,
      offset: Int
  ): Vector[Notification] =
    Using.resource(connection.prepareStatement(listForRecipientSql)) { statement =>
      statement.setString(1, recipientPlayerId.value)
      statement.setBoolean(2, unreadOnly)
      statement.setInt(3, limit.max(1).min(100))
      statement.setInt(4, offset.max(0))
      Using.resource(statement.executeQuery())(readNotifications)
    }

  private val countUnreadSql: String =
    """
      |select count(*) as unread_count
      |from notifications
      |where recipient_player_id = ?
      |  and read_at is null
      |  and (expires_at is null or expires_at > now())
      |""".stripMargin

  private[notification] def countUnread(connection: Connection, recipientPlayerId: PlayerId): Int =
    Using.resource(connection.prepareStatement(countUnreadSql)) { statement =>
      statement.setString(1, recipientPlayerId.value)
      Using.resource(statement.executeQuery()) { resultSet =>
        if resultSet.next() then resultSet.getInt("unread_count")
        else 0
      }
    }

  private val markAllReadSql: String =
    """
      |update notifications
      |set read_at = ?,
      |  payload = jsonb_set(payload, '{readAt}', to_jsonb(?::text), true)
      |where recipient_player_id = ?
      |  and read_at is null
      |""".stripMargin

  private[notification] def markAllRead(
      connection: Connection,
      recipientPlayerId: PlayerId,
      readAt: Instant
  ): Int =
    Using.resource(connection.prepareStatement(markAllReadSql)) { statement =>
      statement.setTimestamp(1, Timestamp.from(readAt))
      statement.setString(2, readAt.toString)
      statement.setString(3, recipientPlayerId.value)
      statement.executeUpdate()
    }

  private val markReadSql: String =
    """
      |update notifications
      |set read_at = coalesce(read_at, ?),
      |  payload = case
      |    when read_at is null then jsonb_set(payload, '{readAt}', to_jsonb(?::text), true)
      |    else payload
      |  end
      |where id = ? and recipient_player_id = ?
      |returning payload
      |""".stripMargin

  private[notification] def markRead(
      connection: Connection,
      notificationId: String,
      recipientPlayerId: PlayerId,
      readAt: Instant
  ): Option[Notification] =
    Using.resource(connection.prepareStatement(markReadSql)) { statement =>
      statement.setTimestamp(1, Timestamp.from(readAt))
      statement.setString(2, readAt.toString)
      statement.setString(3, notificationId)
      statement.setString(4, recipientPlayerId.value)
      Using.resource(statement.executeQuery()) { resultSet =>
        if resultSet.next() then Some(readNotification(resultSet))
        else None
      }
    }

  private def setNullableInstant(statement: PreparedStatement, index: Int, value: Option[Instant]): Unit =
    value match
      case Some(actual) => statement.setTimestamp(index, Timestamp.from(actual))
      case None         => statement.setNull(index, Types.TIMESTAMP_WITH_TIMEZONE)

  private def readNotifications(resultSet: ResultSet): Vector[Notification] =
    @tailrec
    def loop(acc: Vector[Notification]): Vector[Notification] =
      if resultSet.next() then loop(readNotification(resultSet) +: acc)
      else acc.reverse

    loop(Vector.empty)

  private def readNotification(resultSet: ResultSet): Notification =
    read[Notification](resultSet.getString("payload"))
