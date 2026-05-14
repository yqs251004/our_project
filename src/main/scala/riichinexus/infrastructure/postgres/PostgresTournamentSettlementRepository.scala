package riichinexus.infrastructure.postgres

import java.sql.SQLException
import java.sql.Timestamp

import scala.util.Using

import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.application.ports.*
import riichinexus.domain.model.*
import upickle.default.read
final class PostgresTournamentSettlementRepository(
    protected val connectionFactory: JdbcConnectionFactory
) extends TournamentSettlementRepository
    with JdbcRepositorySupport:
  override def save(snapshot: TournamentSettlementSnapshot): TournamentSettlementSnapshot =
    val persisted = snapshot.copy(version = snapshot.version + 1)
    val sql =
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

    val rowsUpdated = withConnection { connection =>
      Using.resource(connection.prepareStatement(sql)) { statement =>
        statement.setString(1, persisted.id.value)
        statement.setString(2, persisted.tournamentId.value)
        statement.setString(3, persisted.stageId.value)
        statement.setTimestamp(4, Timestamp.from(persisted.generatedAt))
        statement.setString(5, writeJson[TournamentSettlementSnapshot](persisted))
        statement.setInt(6, snapshot.version)
        statement.executeUpdate()
      }
    }

    requireOptimisticUpdate(
      rowsUpdated,
      "tournament-settlement",
      persisted.id.value,
      snapshot.version,
      findById(persisted.id).map(_.version)
    )
    persisted

  override def findById(id: SettlementSnapshotId): Option[TournamentSettlementSnapshot] =
    readOne[TournamentSettlementSnapshot](
      "select payload from tournament_settlements where id = ?",
      { statement =>
        statement.setString(1, id.value)
      }
    )

  override def findByTournamentAndStage(
      tournamentId: TournamentId,
      stageId: TournamentStageId
  ): Option[TournamentSettlementSnapshot] =
    readOne[TournamentSettlementSnapshot](
      """
        |select payload
        |from tournament_settlements
        |where tournament_id = ? and stage_id = ?
        |order by generated_at desc, id desc
        |limit 1
        |""".stripMargin,
      { statement =>
        statement.setString(1, tournamentId.value)
        statement.setString(2, stageId.value)
      }
    )

  override def findByTournament(tournamentId: TournamentId): Vector[TournamentSettlementSnapshot] =
    readAll[TournamentSettlementSnapshot](
      "select payload from tournament_settlements where tournament_id = ? order by generated_at desc",
      { statement =>
        statement.setString(1, tournamentId.value)
      }
    )

  override def findAll(): Vector[TournamentSettlementSnapshot] =
    readAll[TournamentSettlementSnapshot](
      "select payload from tournament_settlements order by generated_at desc"
    )

object PostgresTournamentSettlementRepository:
  def apply(connectionFactory: JdbcConnectionFactory): PostgresTournamentSettlementRepository =
    new PostgresTournamentSettlementRepository(connectionFactory)
