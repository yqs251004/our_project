package riichinexus.microservices.tournament.api.matchrecord.`private`
import cats.effect.IO
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.microservices.club.objects.profile.ClubId
import riichinexus.system.json.JsonCodecs.given
import riichinexus.microservices.tournament.domain.competition.functions.TournamentPrivateViewFunctions
import riichinexus.microservices.tournament.objects.matchrecord.`private`.MatchRecordPrivateView
import riichinexus.microservices.tournament.tables.matchrecord.MatchRecordTable
/** 供后端服务读取某俱乐部最近比赛记录 private read model 列表。 */
final case class ListRecentClubMatchRecordsPrivateAPIMessage(
    clubId: ClubId,
    limit: Int
) extends APIMessage[Vector[MatchRecordPrivateView]]:

  override def plan(context: ApiPlanContext): IO[Vector[MatchRecordPrivateView]] =
    for
      records <- IO.blocking(MatchRecordTable.findRecentByClub(context.connection, clubId, limit))
    yield records.map(TournamentPrivateViewFunctions.fromMatchRecord)
