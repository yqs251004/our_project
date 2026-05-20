package riichinexus.microservices.tournament.appeal.api

import java.util.NoSuchElementException

import cats.effect.IO

import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.tournament.appeal.objects.apiTypes.*
import upickle.default.*

final case class AppealAdjudicateAPIMessage(
    appealId: String,
    operatorId: String,
    decision: String,
    verdict: String,
    tableResolution: Option[String] = None,
    note: Option[String] = None
) extends APIMessage[AppealTicketView] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[AppealTicketView] =
    IO {
      val request = AdjudicateAppealRequest(operatorId, decision, verdict, tableResolution, note)
      context.support.tournamentAppealModule.service.adjudicateAppeal(
        ticketId = AppealTicketId(appealId),
        decision = request.decisionType,
        verdict = request.verdict,
        actor = context.support.principal(request.operator),
        tableResolution = request.resolution,
        note = request.note
      ).map(AppealTicketView.fromDomain)
        .getOrElse(throw NoSuchElementException("Resource not found"))
    }
