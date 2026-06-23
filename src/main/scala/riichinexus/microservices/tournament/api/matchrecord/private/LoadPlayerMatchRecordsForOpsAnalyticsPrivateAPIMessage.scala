package riichinexus.microservices.tournament.api.matchrecord.`private`
import cats.effect.IO
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.microservices.player.objects.PlayerId
import riichinexus.system.json.JsonCodecs.given
import riichinexus.microservices.tournament.domain.competition.functions.TournamentPrivateViewFunctions
import riichinexus.microservices.tournament.objects.matchrecord.`private`.MatchRecordPrivateView
import riichinexus.microservices.tournament.tables.matchrecord.MatchRecordTable
/** 供 opsanalytics 后端统计读取某玩家完整比赛记录 private read model 列表。 */
final case class LoadPlayerMatchRecordsForOpsAnalyticsPrivateAPIMessage(
    playerId: PlayerId
) extends APIMessage[Vector[MatchRecordPrivateView]]:

  override def plan(context: ApiPlanContext): IO[Vector[MatchRecordPrivateView]] =
    for
      records <- IO.blocking(MatchRecordTable.findByPlayer(context.connection, playerId))
    yield records.map(TournamentPrivateViewFunctions.fromMatchRecord)
