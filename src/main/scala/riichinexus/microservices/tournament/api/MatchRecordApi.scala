package riichinexus.microservices.tournament.api

import riichinexus.domain.model.*
import riichinexus.microservices.tournament.objects.MatchRecordListQuery
import riichinexus.microservices.tournament.tables.TournamentTables

object MatchRecordApi:

  def listRecords(
      tables: TournamentTables,
      query: MatchRecordListQuery
  ): Vector[MatchRecord] =
    tables.listMatchRecords()
      .filter(record => query.playerId.forall(record.playerIds.contains))
      .filter(record => query.tournamentId.forall(_ == record.tournamentId))
      .filter(record => query.stageId.forall(_ == record.stageId))
      .filter(record => query.tableId.forall(_ == record.tableId))
      .sortBy(record => (record.generatedAt, record.id.value))

  def findRecord(
      tables: TournamentTables,
      recordId: MatchRecordId
  ): Option[MatchRecord] =
    tables.findMatchRecord(recordId)

  def findByTable(
      tables: TournamentTables,
      tableId: TableId
  ): Option[MatchRecord] =
    tables.findMatchRecordByTable(tableId)
