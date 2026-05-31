package riichinexus.microservices.tournament.appeal.tables.appealticket

import java.sql.{Connection, ResultSet}

import scala.annotation.tailrec
import scala.util.Using

import riichinexus.application.ports.OptimisticConcurrencyException
import riichinexus.domain.model.*
import riichinexus.microservices.tournament.appeal.domain.model.*
import riichinexus.microservices.tournament.domain.lineupmanagement.model.*
import riichinexus.microservices.tournament.domain.recordmanagement.model.*
import riichinexus.microservices.tournament.domain.settlementmanagement.model.*
import riichinexus.microservices.tournament.domain.tablemanagement.model.*
import riichinexus.microservices.tournament.domain.tournamentmanagement.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import upickle.default.{read, write}

object AppealTicketTable:
  private val upsertSql: String =
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

  private[appeal] def save(connection: Connection, ticket: AppealTicket): AppealTicket =
    val persisted = ticket.copy(version = ticket.version + 1)
    val rowsUpdated = Using.resource(connection.prepareStatement(upsertSql)) { statement =>
      statement.setString(1, persisted.id.value)
      statement.setString(2, persisted.tableId.value)
      statement.setString(3, persisted.tournamentId.value)
      statement.setString(4, persisted.stageId.value)
      statement.setString(5, persisted.status.toString)
      statement.setString(6, persisted.openedBy.value)
      statement.setString(7, write[AppealTicket](persisted))
      statement.setInt(8, ticket.version)
      statement.executeUpdate()
    }
    if rowsUpdated == 0 then
      throw OptimisticConcurrencyException(
        aggregateType = "appeal-ticket",
        aggregateId = persisted.id.value,
        expectedVersion = ticket.version,
        actualVersion = findById(connection, persisted.id).map(_.version)
      )
    persisted

  private val findByIdSql: String =
    """
      |select payload
      |from appeal_tickets
      |where id = ?
      |""".stripMargin

  private[appeal] def findById(connection: Connection, id: AppealTicketId): Option[AppealTicket] =
    Using.resource(connection.prepareStatement(findByIdSql)) { statement =>
      statement.setString(1, id.value)
      Using.resource(statement.executeQuery()) { resultSet =>
        if resultSet.next() then Some(readTicket(resultSet))
        else None
      }
    }

  private val findAllSql: String =
    """
      |select payload
      |from appeal_tickets
      |order by updated_at desc
      |""".stripMargin

  private[appeal] def findAll(connection: Connection): Vector[AppealTicket] =
    Using.resource(connection.prepareStatement(findAllSql)) { statement =>
      Using.resource(statement.executeQuery())(readTickets)
    }

  private def readTickets(resultSet: ResultSet): Vector[AppealTicket] =
    @tailrec
    def loop(acc: Vector[AppealTicket]): Vector[AppealTicket] =
      if resultSet.next() then loop(readTicket(resultSet) +: acc)
      else acc.reverse

    loop(Vector.empty)

  private def readTicket(resultSet: ResultSet): AppealTicket =
    read[AppealTicket](resultSet.getString("payload"))
