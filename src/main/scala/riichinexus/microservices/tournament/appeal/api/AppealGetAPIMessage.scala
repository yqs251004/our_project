package riichinexus.microservices.tournament.appeal.api

import java.util.NoSuchElementException

import cats.effect.IO

import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.microservices.tournament.appeal.objects.ticketmanagement.AppealTicketId
import riichinexus.microservices.tournament.appeal.domain.model.AppealTicket

import riichinexus.microservices.tournament.appeal.objects.apiTypes.AppealTicketView
import riichinexus.microservices.tournament.appeal.tables.appealticket.AppealTicketTable
import upickle.default.ReadWriter

/** 读取单个申诉工单详情。 */
final case class AppealGetAPIMessage(appealId: String) extends APIMessage[AppealTicketView] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[AppealTicketView] =
    for
      ticketId <- IO.delay(AppealTicketId(appealId))
      ticket <- IO.blocking(findAppeal(context, ticketId))
    yield AppealTicketView.fromDomain(ticket)

  private def findAppeal(context: ApiPlanContext, ticketId: AppealTicketId): AppealTicket =
    AppealTicketTable
      .findById(context.connection, ticketId)
      .getOrElse(throw NoSuchElementException("Resource not found"))
