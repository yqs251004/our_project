package riichinexus.microservices.tournament.domain.finalization.functions

import java.sql.Connection

import riichinexus.microservices.notification.objects.NotificationType
import riichinexus.microservices.notification.objects.`private`.CreateNotificationRequest
import riichinexus.microservices.tournament.domain.finalization.model.TournamentSettlementSnapshot
import riichinexus.microservices.tournament.tables.tournaments.TournamentTable

/** TournamentSettlementNotificationRequestFunctions 提供赛事结算通知请求相关的领域计算、校验和转换函数。 */

private[tournament] object TournamentSettlementNotificationRequestFunctions:

  def finalized(
      connection: Connection,
      snapshot: TournamentSettlementSnapshot
  ): Vector[CreateNotificationRequest] =
    val tournamentName =
      TournamentTable
        .findById(connection, snapshot.tournamentId)
        .map(_.name)
        .getOrElse(snapshot.tournamentId.value)

    snapshot.entries.map { entry =>
      CreateNotificationRequest(
        recipientPlayerId = entry.playerId.value,
        notificationType = NotificationType.TournamentSettlementFinalized,
        title = "赛事结算已完成",
        body = s"$tournamentName 已完成结算：你的排名第 ${entry.rank}，结算分 ${entry.finalPoints}。",
        severity = Some("info"),
        sourceService = "tournament",
        sourceType = "tournament-settlement",
        sourceId = snapshot.id.value,
        actionUrl = Some(s"/public/tournaments/${snapshot.tournamentId.value}"),
        objects = Map(
          "tournamentId" -> snapshot.tournamentId.value,
          "stageId" -> snapshot.stageId.value,
          "settlementId" -> snapshot.id.value,
          "playerId" -> entry.playerId.value,
          "rank" -> entry.rank.toString,
          "finalPoints" -> entry.finalPoints.toString,
          "awardAmount" -> entry.awardAmount.toString
        )
      )
    }
