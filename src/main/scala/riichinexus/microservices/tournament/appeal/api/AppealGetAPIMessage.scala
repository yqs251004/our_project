package riichinexus.microservices.tournament.appeal.api

import java.util.NoSuchElementException

import cats.effect.IO

import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.domain.model.*
import riichinexus.microservices.tournament.appeal.domain.model.*
import riichinexus.microservices.tournament.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.tournament.appeal.objects.apiTypes.*
import riichinexus.microservices.tournament.appeal.tables.appealticket.AppealTicketTable
import upickle.default.*

final case class AppealGetAPIMessage(appealId: String) extends APIMessage[AppealTicketView] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[AppealTicketView] =
    for
      ticketId <- IO.blocking(AppealTicketId(appealId))
      ticket <- IO.blocking(findAppeal(context, ticketId))
    yield AppealTicketView.fromDomain(ticket)

  private def findAppeal(context: ApiPlanContext, ticketId: AppealTicketId): AppealTicket =
    AppealTicketTable
      .findById(context.connection, ticketId)
      .getOrElse(throw NoSuchElementException("Resource not found"))
