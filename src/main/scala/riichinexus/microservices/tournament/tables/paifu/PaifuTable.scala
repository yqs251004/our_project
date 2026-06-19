package riichinexus.microservices.tournament.tables.paifu

import java.sql.{Connection, ResultSet, Timestamp}

import scala.annotation.tailrec
import scala.util.Using
import scala.util.control.NonFatal

import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.microservices.tournament.objects.paifu.PaifuId
import riichinexus.microservices.tournament.objects.stage.table.TableId
import riichinexus.microservices.tournament.domain.paifu.functions.PaifuFunctions
import riichinexus.system.json.JsonCodecs.given
import riichinexus.microservices.tournament.objects.paifu.Paifu
import upickle.default.{read, write}

object PaifuTable:
  private val upsertSql: String =
    """
      |insert into paifus (id, table_id, tournament_id, stage_id, recorded_at, player_ids, payload, updated_at)
      |values (?, ?, ?, ?, ?, ?, cast(? as jsonb), now())
      |on conflict (id) do update set
      |  table_id = excluded.table_id,
      |  tournament_id = excluded.tournament_id,
      |  stage_id = excluded.stage_id,
      |  recorded_at = excluded.recorded_at,
      |  player_ids = excluded.player_ids,
      |  payload = excluded.payload,
      |  updated_at = now()
      |""".stripMargin

  private[tournament] def save(connection: Connection, paifu: Paifu): Paifu =
    Using.resource(connection.prepareStatement(upsertSql)) { statement =>
      statement.setString(1, paifu.id.value)
      statement.setString(2, paifu.metadata.tableId.value)
      statement.setString(3, paifu.metadata.tournamentId.value)
      statement.setString(4, paifu.metadata.stageId.value)
      statement.setTimestamp(5, Timestamp.from(paifu.metadata.recordedAt))
      statement.setArray(6, connection.createArrayOf("text", PaifuFunctions.playerIds(paifu).map(_.value).toArray))
      statement.setString(7, write[Paifu](paifu))
      statement.executeUpdate()
    }
    paifu

  private val findByIdSql: String =
    """
      |select payload
      |from paifus
      |where id = ?
      |""".stripMargin

  private[tournament] def findById(connection: Connection, id: PaifuId): Option[Paifu] =
    Using.resource(connection.prepareStatement(findByIdSql)) { statement =>
      statement.setString(1, id.value)
      Using.resource(statement.executeQuery()) { resultSet =>
        if resultSet.next() then Some(readPaifu(resultSet))
        else None
      }
    }

  private val deleteByTableSql: String =
    """
      |delete from paifus
      |where table_id = ?
      |""".stripMargin

  private[tournament] def deleteByTable(connection: Connection, tableId: TableId): Unit =
    Using.resource(connection.prepareStatement(deleteByTableSql)) { statement =>
      statement.setString(1, tableId.value)
      statement.executeUpdate()
      ()
    }

  private val findAllSql: String =
    """
      |select payload
      |from paifus
      |order by recorded_at desc
      |""".stripMargin

  private[tournament] def findAll(connection: Connection): Vector[Paifu] =
    Using.resource(connection.prepareStatement(findAllSql)) { statement =>
      Using.resource(statement.executeQuery())(readPaifus)
    }

  private val findByPlayerSql: String =
    """
      |select payload
      |from paifus
      |where ? = any(player_ids)
      |order by recorded_at desc
      |""".stripMargin

  private[tournament] def findByPlayer(connection: Connection, playerId: PlayerId): Vector[Paifu] =
    Using.resource(connection.prepareStatement(findByPlayerSql)) { statement =>
      statement.setString(1, playerId.value)
      Using.resource(statement.executeQuery())(readPaifus)
    }

  private def readPaifus(resultSet: ResultSet): Vector[Paifu] =
    @tailrec
    def loop(acc: Vector[Paifu]): Vector[Paifu] =
      if resultSet.next() then
        val nextAcc =
          try readPaifu(resultSet) +: acc
          catch case NonFatal(_) => acc
        loop(nextAcc)
      else acc.reverse

    loop(Vector.empty)

  private def readPaifu(resultSet: ResultSet): Paifu =
    read[Paifu](resultSet.getString("payload"))
