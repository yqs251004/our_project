package riichinexus.infrastructure.postgres

import java.sql.SQLException
import java.sql.Timestamp

import scala.util.Using

import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.application.ports.*
import riichinexus.domain.model.*
import upickle.default.read
final class PostgresAdvancedStatsBoardRepository(
    protected val connectionFactory: JdbcConnectionFactory
) extends AdvancedStatsBoardRepository
    with JdbcRepositorySupport:
  override def save(board: AdvancedStatsBoard): AdvancedStatsBoard =
    val persisted = board.copy(version = board.version + 1)
    val sql =
      """
        |insert into advanced_stats_boards (owner_key, owner_type, payload, updated_at)
        |values (?, ?, cast(? as jsonb), now())
        |on conflict (owner_key) do update set
        |  owner_type = excluded.owner_type,
        |  payload = excluded.payload,
        |  updated_at = now()
        |where cast(advanced_stats_boards.payload ->> 'version' as integer) = ?
        |""".stripMargin

    val rowsUpdated = withConnection { connection =>
      Using.resource(connection.prepareStatement(sql)) { statement =>
        statement.setString(1, ownerKey(persisted.owner))
        statement.setString(2, ownerType(persisted.owner))
        statement.setString(3, writeJson[AdvancedStatsBoard](persisted))
        statement.setInt(4, board.version)
        statement.executeUpdate()
      }
    }

    requireOptimisticUpdate(
      rowsUpdated,
      "advanced-stats-board",
      ownerKey(persisted.owner),
      board.version,
      findByOwner(persisted.owner).map(_.version)
    )
    persisted

  override def findByOwner(owner: DashboardOwner): Option[AdvancedStatsBoard] =
    readOne[AdvancedStatsBoard](
      "select payload from advanced_stats_boards where owner_key = ?",
      { statement =>
        statement.setString(1, ownerKey(owner))
      }
    )

  override def findAll(): Vector[AdvancedStatsBoard] =
    readAll[AdvancedStatsBoard]("select payload from advanced_stats_boards order by owner_key")

  private def ownerKey(owner: DashboardOwner): String =
    owner match
      case DashboardOwner.Player(playerId) => s"player:${playerId.value}"
      case DashboardOwner.Club(clubId)     => s"club:${clubId.value}"

  private def ownerType(owner: DashboardOwner): String =
    owner match
      case DashboardOwner.Player(_) => "player"
      case DashboardOwner.Club(_)   => "club"

object PostgresAdvancedStatsBoardRepository:
  def apply(connectionFactory: JdbcConnectionFactory): PostgresAdvancedStatsBoardRepository =
    new PostgresAdvancedStatsBoardRepository(connectionFactory)
