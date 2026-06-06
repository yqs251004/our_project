package riichinexus.microservices.tournament.api

import java.time.Instant
import java.util.NoSuchElementException

import cats.effect.IO
import riichinexus.microservices.auth.domain.functions.AuthorizationPolicyFunctions
import riichinexus.microservices.auth.objects.Permission
import riichinexus.microservices.auth.utils.ResolveAccessPrincipal
import riichinexus.microservices.opsanalytics.api.`private`.RefreshOpsAnalyticsAfterMatchArchivedPrivateAPIMessage
import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.microservices.tournament.appeal.api.AppealListAPIMessage
import riichinexus.microservices.tournament.appeal.objects.{AppealStatus as AppealViewStatus}
import riichinexus.microservices.tournament.appeal.objects.apiTypes.AppealListQuery
import riichinexus.microservices.tournament.domain.tablemanagement.functions.{TableFunctions, TournamentStageTableScheduler}
import riichinexus.microservices.tournament.domain.tablemanagement.model.Table
import riichinexus.microservices.tournament.objects.tablemanagement.{TableId, TableStatus}
import riichinexus.microservices.tournament.objects.tablemanagement.apiTypes.TournamentTableView
import riichinexus.microservices.tournament.tables.matchrecord.MatchRecordTable
import riichinexus.microservices.tournament.tables.tournamentgame.TournamentGameTable
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.system.json.JsonCodecs.given
import upickle.default.*

final case class TournamentTableFinalizeArchiveAPIMessage(
    tableId: String,
    operatorId: String
) extends APIMessage[TournamentTableView] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[TournamentTableView] =
    val archivedAt = Instant.now()
    for
      actor <- IO.blocking(ResolveAccessPrincipal(PlayerId(operatorId)).resolve(context.connection))
      appealPage <- AppealListAPIMessage(
        AppealListQuery(
          tableId = Some(TableId(tableId)),
          limit = Some(100),
          offset = Some(0)
        )
      ).plan(context)
      archived <- IO.blocking {
        val table = TournamentGameTable.findById(context.connection, TableId(tableId))
          .getOrElse(throw NoSuchElementException("Resource not found"))

        AuthorizationPolicyFunctions.requirePermission(
          AuthorizationPolicyFunctions.strict,
          actor,
          Permission.ManageTournamentStages,
          tournamentId = Some(table.tournamentId)
        )

        finalizeArchive(
          context.connection,
          table,
          archivedAt,
          activeAppealExists = appealPage.items.exists(appeal =>
            appeal.status == AppealViewStatus.Open ||
              appeal.status == AppealViewStatus.UnderReview ||
              appeal.status == AppealViewStatus.Escalated
          )
        )
      }
      _ <- RefreshOpsAnalyticsAfterMatchArchivedPrivateAPIMessage(
        matchRecord = archived.matchRecord,
        occurredAt = archived.matchRecord.generatedAt
      ).plan(context)
    yield TournamentTableView.fromDomain(archived.table)

  private def finalizeArchive(
      connection: java.sql.Connection,
      table: Table,
      archivedAt: Instant,
      activeAppealExists: Boolean
  ): ArchivedTable =
    require(table.status == TableStatus.Scoring, s"Only scoring table ${table.id.value} can be archived")
    require(
      !activeAppealExists,
      s"Table ${table.id.value} cannot be archived while appeals are active"
    )

    val recordId = table.matchRecordId
      .getOrElse(throw IllegalArgumentException(s"Table ${table.id.value} has no match record to archive"))
    val paifuId = table.paifuId
      .getOrElse(throw IllegalArgumentException(s"Table ${table.id.value} has no paifu to archive"))
    val record = MatchRecordTable.findById(connection, recordId)
      .getOrElse(throw NoSuchElementException(s"Match record ${recordId.value} was not found"))

    val archivedTable = TournamentGameTable.save(
      connection,
      TableFunctions.archive(
        table,
        recordId = recordId,
        paifuId = paifuId,
        at = archivedAt,
        note = Some("archived after appeal review")
      )
    )
    TournamentStageTableScheduler.progressAfterTableArchived(connection, archivedTable, archivedAt)
    ArchivedTable(archivedTable, record)

  private final case class ArchivedTable(
      table: Table,
      matchRecord: riichinexus.microservices.tournament.domain.recordmanagement.model.MatchRecord
  )
