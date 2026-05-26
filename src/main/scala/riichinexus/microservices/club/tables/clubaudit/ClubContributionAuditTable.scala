package riichinexus.microservices.club.tables.clubaudit

import java.sql.{Connection, ResultSet}

import scala.annotation.tailrec
import scala.util.Using

import riichinexus.domain.model.{AuditEventEntry, ClubId}
import riichinexus.infrastructure.json.JsonCodecs.given
import upickle.default.read

object ClubContributionAuditTable:
  private val findContributionChangesSql: String =
    """
      |select payload
      |from club_contribution_audit_entries
      |where club_id = ?
      |order by occurred_at asc, id asc
      |""".stripMargin

  private[riichinexus] def findContributionChanges(
      connection: Connection,
      clubId: ClubId
  ): Vector[AuditEventEntry] =
    Using.resource(connection.prepareStatement(findContributionChangesSql)) { statement =>
      statement.setString(1, clubId.value)
      Using.resource(statement.executeQuery())(readEntries)
    }

  private def readEntries(resultSet: ResultSet): Vector[AuditEventEntry] =
    @tailrec
    def loop(acc: Vector[AuditEventEntry]): Vector[AuditEventEntry] =
      if resultSet.next() then loop(readEntry(resultSet) +: acc)
      else acc.reverse

    loop(Vector.empty)

  private def readEntry(resultSet: ResultSet): AuditEventEntry =
    read[AuditEventEntry](resultSet.getString("payload"))
