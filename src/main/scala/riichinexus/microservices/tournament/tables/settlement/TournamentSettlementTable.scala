package riichinexus.microservices.tournament.tables.settlement

import java.sql.{Connection, ResultSet, Timestamp}

import scala.annotation.tailrec
import scala.util.Using

import riichinexus.application.ports.OptimisticConcurrencyException
import riichinexus.domain.model.*
import riichinexus.microservices.tournament.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import upickle.default.{read, write}

object TournamentSettlementTable:
  private val upsertSql: String =
    """
      |insert into tournament_settlements (id, tournament_id, stage_id, generated_at, payload, updated_at)
      |values (?, ?, ?, ?, cast(? as jsonb), now())
      |on conflict (id) do update set
      |  tournament_id = excluded.tournament_id,
      |  stage_id = excluded.stage_id,
      |  generated_at = excluded.generated_at,
      |  payload = excluded.payload,
      |  updated_at = now()
      |where cast(tournament_settlements.payload ->> 'version' as integer) = ?
      |""".stripMargin

  private[riichinexus] def save(
      connection: Connection,
      snapshot: TournamentSettlementSnapshot
  ): TournamentSettlementSnapshot =
    val persisted = snapshot.copy(version = snapshot.version + 1)
    val rowsUpdated = Using.resource(connection.prepareStatement(upsertSql)) { statement =>
      statement.setString(1, persisted.id.value)
      statement.setString(2, persisted.tournamentId.value)
      statement.setString(3, persisted.stageId.value)
      statement.setTimestamp(4, Timestamp.from(persisted.generatedAt))
      statement.setString(5, write[TournamentSettlementSnapshot](persisted))
      statement.setInt(6, snapshot.version)
      statement.executeUpdate()
    }
    if rowsUpdated == 0 then
      throw OptimisticConcurrencyException(
        aggregateType = "tournament-settlement",
        aggregateId = persisted.id.value,
        expectedVersion = snapshot.version,
        actualVersion = findById(connection, persisted.id).map(_.version)
      )
    persisted

  private val findByIdSql: String =
    """
      |select payload
      |from tournament_settlements
      |where id = ?
      |""".stripMargin

  private[riichinexus] def findById(connection: Connection, id: SettlementSnapshotId): Option[TournamentSettlementSnapshot] =
    Using.resource(connection.prepareStatement(findByIdSql)) { statement =>
      statement.setString(1, id.value)
      Using.resource(statement.executeQuery()) { resultSet =>
        if resultSet.next() then Some(readSnapshot(resultSet))
        else None
      }
    }

  private val findByTournamentAndStageSql: String =
    """
      |select payload
      |from tournament_settlements
      |where tournament_id = ? and stage_id = ?
      |order by generated_at desc, id desc
      |limit 1
      |""".stripMargin

  private[riichinexus] def findByTournamentAndStage(
      connection: Connection,
      tournamentId: TournamentId,
      stageId: TournamentStageId
  ): Option[TournamentSettlementSnapshot] =
    Using.resource(connection.prepareStatement(findByTournamentAndStageSql)) { statement =>
      statement.setString(1, tournamentId.value)
      statement.setString(2, stageId.value)
      Using.resource(statement.executeQuery()) { resultSet =>
        if resultSet.next() then Some(readSnapshot(resultSet))
        else None
      }
    }

  private val findByTournamentSql: String =
    """
      |select payload
      |from tournament_settlements
      |where tournament_id = ?
      |order by generated_at desc
      |""".stripMargin

  private[riichinexus] def findByTournament(
      connection: Connection,
      tournamentId: TournamentId
  ): Vector[TournamentSettlementSnapshot] =
    Using.resource(connection.prepareStatement(findByTournamentSql)) { statement =>
      statement.setString(1, tournamentId.value)
      Using.resource(statement.executeQuery())(readSnapshots)
    }

  private val findAllSql: String =
    """
      |select payload
      |from tournament_settlements
      |order by generated_at desc
      |""".stripMargin

  private[riichinexus] def findAll(connection: Connection): Vector[TournamentSettlementSnapshot] =
    Using.resource(connection.prepareStatement(findAllSql)) { statement =>
      Using.resource(statement.executeQuery())(readSnapshots)
    }

  private def readSnapshots(resultSet: ResultSet): Vector[TournamentSettlementSnapshot] =
    @tailrec
    def loop(acc: Vector[TournamentSettlementSnapshot]): Vector[TournamentSettlementSnapshot] =
      if resultSet.next() then loop(readSnapshot(resultSet) +: acc)
      else acc.reverse

    loop(Vector.empty)

  private def readSnapshot(resultSet: ResultSet): TournamentSettlementSnapshot =
    read[TournamentSettlementSnapshot](resultSet.getString("payload"))
