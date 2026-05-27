package riichinexus.microservices.tournament.api

import java.util.NoSuchElementException

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.domain.model.*
import riichinexus.microservices.tournament.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.tournament.objects.apiTypes.*
import riichinexus.microservices.tournament.objects.apiTypes.*
import riichinexus.microservices.tournament.objects.apiTypes.AssignTournamentAdminRequest.given
import riichinexus.microservices.tournament.tables.matchrecord.MatchRecordTable
import upickle.default.*

final case class TournamentRecordGetByTableAPIMessage(tableId: String) extends APIMessage[TournamentMatchRecordView] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[TournamentMatchRecordView] =
    for
      id <- IO(TableId(tableId))
      record <- IO(resolveRecord(context, id))
    yield TournamentMatchRecordView.fromDomain(record)

  private def resolveRecord(context: ApiPlanContext, tableId: TableId): MatchRecord =
    MatchRecordTable.findByTable(context.connection, tableId)
      .getOrElse(throw NoSuchElementException("Resource not found"))
