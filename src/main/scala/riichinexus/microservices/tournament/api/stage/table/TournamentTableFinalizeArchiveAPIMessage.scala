package riichinexus.microservices.tournament.api.stage.table
import riichinexus.microservices.tournament.domain.competition.functions.TournamentViewFunctions
import java.time.Instant
import java.util.NoSuchElementException

import cats.effect.IO
import riichinexus.microservices.auth.objects.authorization.Permission
import riichinexus.microservices.auth.api.authorization.`private`.RequirePermissionPrivateAPIMessage
import riichinexus.microservices.auth.api.authorization.`private`.ResolveAccessPrincipalPrivateAPIMessage
import riichinexus.microservices.opsanalytics.api.`private`.RefreshOpsAnalyticsAfterMatchArchivedPrivateAPIMessage
import riichinexus.microservices.player.objects.PlayerId
import riichinexus.microservices.tournament.appeal.api.AppealListAPIMessage
import riichinexus.microservices.tournament.appeal.objects.{AppealStatus as AppealViewStatus}
import riichinexus.microservices.tournament.appeal.objects.apiTypes.AppealListQuery
import riichinexus.microservices.tournament.domain.competition.functions.TournamentPrivateViewFunctions
import riichinexus.microservices.tournament.domain.stage.functions.scheduling.{TableFunctions, TournamentStageTableScheduler}
import riichinexus.microservices.tournament.domain.matchrecord.model.MatchRecord
import riichinexus.microservices.tournament.domain.stage.model.Table
import riichinexus.microservices.tournament.objects.stage.table.{TableId, TableStatus}
import riichinexus.microservices.tournament.objects.stage.table.TournamentTableView
import riichinexus.microservices.tournament.tables.matchrecord.MatchRecordTable
import riichinexus.microservices.tournament.tables.tournamentgame.TournamentGameTable
import riichinexus.system.api.{APIMessage, ApiPlanContext}

/** 归档已计分牌桌并刷新赛后统计。 */
final case class TournamentTableFinalizeArchiveAPIMessage(
    tableId: String,
    operatorId: String
) extends APIMessage[TournamentTableView]:

  override def plan(context: ApiPlanContext): IO[TournamentTableView] =
    for
      archivedAt <- IO.realTimeInstant
      actor <- ResolveAccessPrincipalPrivateAPIMessage(PlayerId(operatorId)).plan(context)
      appealPage <- AppealListAPIMessage(
        AppealListQuery(
          tableId = Some(TableId(tableId)),
          limit = Some(100),
          offset = Some(0)
        )
      ).plan(context)
      archived <- finalizeArchive(
        context,
        actor,
        TableId(tableId),
        archivedAt,
        activeAppealExists = appealPage.items.exists(appeal =>
          appeal.status == AppealViewStatus.Open ||
            appeal.status == AppealViewStatus.UnderReview ||
            appeal.status == AppealViewStatus.Escalated
        )
      )
      _ <- RefreshOpsAnalyticsAfterMatchArchivedPrivateAPIMessage(
        matchRecord = TournamentPrivateViewFunctions.fromMatchRecord(archived._2),
        occurredAt = archived._2.generatedAt
      ).plan(context)
    yield TournamentViewFunctions.tableView(archived._1)

  private def finalizeArchive(
      context: ApiPlanContext,
      actor: riichinexus.microservices.auth.objects.authorization.`private`.AccessPrincipalPrivateView,
      tableId: TableId,
      archivedAt: Instant,
      activeAppealExists: Boolean
  ): IO[(Table, MatchRecord)] =
    for
      table <- IO.blocking {
        val table = TournamentGameTable.findById(context.connection, tableId)
          .getOrElse(throw NoSuchElementException("Resource not found"))
        require(table.status == TableStatus.Scoring, s"Only scoring table ${table.id.value} can be archived")
        require(
          !activeAppealExists,
          s"Table ${table.id.value} cannot be archived while appeals are active"
        )
        table
      }
      _ <- RequirePermissionPrivateAPIMessage(
          actor,
          Permission.ManageTournamentStages,
          tournamentId = Some(table.tournamentId)
      ).plan(context)
      archived <- IO.blocking {
        val recordId = table.matchRecordId
          .getOrElse(throw IllegalArgumentException(s"Table ${table.id.value} has no match record to archive"))
        val paifuId = table.paifuId
          .getOrElse(throw IllegalArgumentException(s"Table ${table.id.value} has no paifu to archive"))
        val record = MatchRecordTable.findById(context.connection, recordId)
          .getOrElse(throw NoSuchElementException(s"Match record ${recordId.value} was not found"))

        val archivedTable = TournamentGameTable.save(
          context.connection,
          TableFunctions.archive(
            table,
            recordId = recordId,
            paifuId = paifuId,
            at = archivedAt,
            note = Some("archived after appeal review")
          )
        )
        archivedTable -> record
      }
      _ <- TournamentStageTableScheduler.progressAfterTableArchived(context.connection, archived._1, archivedAt)
    yield archived
