package riichinexus.microservices.tournament.appeal.api

import java.time.Instant
import java.util.NoSuchElementException

import cats.effect.IO

import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.bootstrap.TournamentAppealModuleContext
import riichinexus.domain.model.*
import riichinexus.microservices.auth.domain.model.*
import riichinexus.microservices.tournament.appeal.domain.model.*
import riichinexus.microservices.tournament.domain.model.*
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
    for
      resolved <- IO(resolveInput)
      actor <- IO(context.principal(resolved.operator))
      reopenedAt <- IO.realTimeInstant
      module = context.support.tournamentAppealModule
      command = ReopenAppealCommand(AppealTicketId(appealId), resolved, actor, reopenedAt)
      ticket <- IO(reopenAppeal(context.connection, module, command))
    yield AppealTicketView.fromDomain(ticket)

  private def resolveInput: ReopenAppealRequest =
    ReopenAppealRequest(operatorId, reason, note)

  private def reopenAppeal(
      connection: java.sql.Connection,
      module: TournamentAppealModuleContext,
      command: ReopenAppealCommand
  ): AppealTicket =
    module.service.reopenAppeal(
      connection = connection,
      ticketId = command.ticketId,
      reason = command.input.reason,
      actor = command.actor,
      reopenedAt = command.reopenedAt,
      note = command.input.note
    ).getOrElse(throw NoSuchElementException("Resource not found"))

  private final case class ReopenAppealCommand(
      ticketId: AppealTicketId,
      input: ReopenAppealRequest,
      actor: AccessPrincipal,
      reopenedAt: Instant
  )
