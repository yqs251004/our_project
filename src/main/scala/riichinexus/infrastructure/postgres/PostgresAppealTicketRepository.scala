package riichinexus.infrastructure.postgres

import java.sql.SQLException
import java.sql.Timestamp

import scala.util.Using

import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.application.ports.*
import riichinexus.domain.model.*
import upickle.default.read
final class PostgresAppealTicketRepository(
    protected val connectionFactory: JdbcConnectionFactory
) extends AppealTicketRepository
    with JdbcRepositorySupport:
  override def save(ticket: AppealTicket): AppealTicket =
    val persisted = ticket.copy(version = ticket.version + 1)
    val sql =
      """
        |insert into appeal_tickets (id, table_id, tournament_id, stage_id, status, opened_by, payload, updated_at)
        |values (?, ?, ?, ?, ?, ?, cast(? as jsonb), now())
        |on conflict (id) do update set
        |  table_id = excluded.table_id,
        |  tournament_id = excluded.tournament_id,
        |  stage_id = excluded.stage_id,
        |  status = excluded.status,
        |  opened_by = excluded.opened_by,
        |  payload = excluded.payload,
        |  updated_at = now()
        |where cast(appeal_tickets.payload ->> 'version' as integer) = ?
        |""".stripMargin

    val rowsUpdated = withConnection { connection =>
      Using.resource(connection.prepareStatement(sql)) { statement =>
        statement.setString(1, persisted.id.value)
        statement.setString(2, persisted.tableId.value)
        statement.setString(3, persisted.tournamentId.value)
        statement.setString(4, persisted.stageId.value)
        statement.setString(5, persisted.status.toString)
        statement.setString(6, persisted.openedBy.value)
        statement.setString(7, writeJson[AppealTicket](persisted))
        statement.setInt(8, ticket.version)
        statement.executeUpdate()
      }
    }

    requireOptimisticUpdate(
      rowsUpdated,
      "appeal-ticket",
      persisted.id.value,
      ticket.version,
      findById(persisted.id).map(_.version)
    )
    persisted

  override def findById(id: AppealTicketId): Option[AppealTicket] =
    readOne[AppealTicket]("select payload from appeal_tickets where id = ?", { statement =>
      statement.setString(1, id.value)
    })

  override def findAll(): Vector[AppealTicket] =
    readAll[AppealTicket]("select payload from appeal_tickets order by updated_at desc")

object PostgresAppealTicketRepository:
  def apply(connectionFactory: JdbcConnectionFactory): PostgresAppealTicketRepository =
    new PostgresAppealTicketRepository(connectionFactory)
