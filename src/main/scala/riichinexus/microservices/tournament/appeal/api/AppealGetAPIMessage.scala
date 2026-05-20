package riichinexus.microservices.tournament.appeal.api

import java.util.NoSuchElementException

import cats.effect.IO

import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.tournament.appeal.objects.apiTypes.*
import upickle.default.*

final case class AppealGetAPIMessage(appealId: String) extends APIMessage[AppealTicketView] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[AppealTicketView] =
    IO {
      context.support.tournamentAppealModule.tables.findAppeal(AppealTicketId(appealId))
        .map(AppealTicketView.fromDomain)
        .getOrElse(throw NoSuchElementException("Resource not found"))
    }
