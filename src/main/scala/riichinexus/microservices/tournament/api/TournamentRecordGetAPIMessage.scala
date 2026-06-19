package riichinexus.microservices.tournament.api

import riichinexus.microservices.tournament.domain.functions.TournamentViewFunctions
import java.util.NoSuchElementException

import cats.effect.IO
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.microservices.tournament.objects.matchrecord.MatchRecordId
import riichinexus.microservices.tournament.domain.matchrecord.model.MatchRecord

import riichinexus.microservices.tournament.objects.matchrecord.apiTypes.TournamentMatchRecordView

import riichinexus.microservices.tournament.tables.matchrecord.MatchRecordTable
/** 获取赛事比赛记录详情。 */
final case class TournamentRecordGetAPIMessage(recordId: String) extends APIMessage[TournamentMatchRecordView]:

  override def plan(context: ApiPlanContext): IO[TournamentMatchRecordView] =
    for
      id <- IO.blocking(MatchRecordId(recordId))
      record <- IO.blocking(resolveRecord(context, id))
    yield TournamentViewFunctions.matchRecordView(record)

  private def resolveRecord(context: ApiPlanContext, recordId: MatchRecordId): MatchRecord =
    MatchRecordTable.findById(context.connection, recordId)
      .getOrElse(throw NoSuchElementException("Resource not found"))
