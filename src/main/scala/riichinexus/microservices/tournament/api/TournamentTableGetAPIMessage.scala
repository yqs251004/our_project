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
import riichinexus.microservices.tournament.tables.tournamentgame.TournamentGameTable
import upickle.default.*

final case class TournamentTableGetAPIMessage(tableId: String) extends APIMessage[TournamentTableView] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[TournamentTableView] =
    for
      id <- IO.blocking(TableId(tableId))
      table <- IO.blocking(resolveTable(context, id))
    yield TournamentTableView.fromDomain(table)

  private def resolveTable(context: ApiPlanContext, tableId: TableId): Table =
    TournamentGameTable.findById(context.connection, tableId)
      .getOrElse(throw NoSuchElementException("Resource not found"))
