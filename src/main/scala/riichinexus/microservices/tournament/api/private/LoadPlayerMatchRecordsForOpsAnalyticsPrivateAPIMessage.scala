package riichinexus.microservices.tournament.api.`private`

import cats.effect.IO
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.system.json.JsonCodecs.given
import riichinexus.microservices.tournament.objects.`private`.MatchRecordPrivateView
import riichinexus.microservices.tournament.tables.matchrecord.MatchRecordTable
import upickle.default.ReadWriter

/** 供 opsanalytics 后端统计读取某玩家完整比赛记录 private read model 列表。 */
final case class LoadPlayerMatchRecordsForOpsAnalyticsPrivateAPIMessage(
    playerId: PlayerId
) extends APIMessage[Vector[MatchRecordPrivateView]] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[Vector[MatchRecordPrivateView]] =
    for
      records <- IO.blocking(MatchRecordTable.findByPlayer(context.connection, playerId))
    yield records.map(TournamentPrivateReadModel.fromMatchRecord)
