package riichinexus.microservices.club.tables.clubaudit

import java.sql.Connection

object ClubContributionAuditTableInitializer:
  private val initTableSql: String =
    """
      |create or replace view club_contribution_audit_entries as
      |select
      |  id,
      |  aggregate_id as club_id,
      |  occurred_at,
      |  payload
      |from audit_events
      |where aggregate_type = 'club'
      |  and event_type = 'ClubMemberContributionAdjusted'
      |;
      |create index if not exists idx_audit_events_club_contribution
      |on audit_events (aggregate_id, occurred_at, id)
      |where aggregate_type = 'club'
      |  and event_type = 'ClubMemberContributionAdjusted'
      |;
      |""".stripMargin

  private[riichinexus] def initialize(connection: Connection): Unit =
    val statement = connection.createStatement()
    try statement.execute(initTableSql)
    finally statement.close()
