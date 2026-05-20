package riichinexus.microservices.tournament.appeal.api

import java.util.NoSuchElementException

import cats.effect.IO

import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.tournament.appeal.objects.apiTypes.*
import upickle.default.*

final case class AppealReopenAPIMessage(
    appealId: String,
    operatorId: String,
    reason: String,
    note: Option[String] = None
) extends APIMessage[AppealTicketView] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[AppealTicketView] =
    IO {
      val request = ReopenAppealRequest(operatorId, reason, note)
      context.support.tournamentAppealModule.service.reopenAppeal(
        ticketId = AppealTicketId(appealId),
        reason = request.reason,
        actor = context.support.principal(request.operator),
        note = request.note
      ).map(AppealTicketView.fromDomain)
        .getOrElse(throw NoSuchElementException("Resource not found"))
    }
