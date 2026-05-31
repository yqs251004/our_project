package riichinexus.microservices.tournament.appeal.api

import java.time.Instant
import java.util.NoSuchElementException

import cats.effect.IO

import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.bootstrap.TournamentAppealModuleContext
import riichinexus.domain.model.*
import riichinexus.microservices.auth.domain.model.*
import riichinexus.microservices.tournament.appeal.domain.model.*
import riichinexus.microservices.tournament.domain.lineupmanagement.model.*
import riichinexus.microservices.tournament.domain.recordmanagement.model.*
import riichinexus.microservices.tournament.domain.settlementmanagement.model.*
import riichinexus.microservices.tournament.domain.tablemanagement.model.*
import riichinexus.microservices.tournament.domain.tournamentmanagement.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.tournament.appeal.objects.apiTypes.*
import upickle.default.*

final case class AppealResolveAPIMessage(
    appealId: String,
    operatorId: String,
    verdict: String,
    note: Option[String] = None
) extends APIMessage[AppealTicketView] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[AppealTicketView] =
    for
      resolved <- IO.blocking(resolveInput)
      actor <- IO.blocking(context.principal(PlayerId(resolved.operatorId)))
      resolvedAt <- IO.realTimeInstant
      module = context.support.tournamentAppealModule
      command = ResolveAppealCommand(AppealTicketId(appealId), resolved, actor, resolvedAt)
      ticket <- IO.blocking(resolveAppeal(context.connection, module, command))
    yield AppealTicketView.fromDomain(ticket)

  private def resolveInput: ResolveAppealRequest =
    ResolveAppealRequest(operatorId, verdict, note)

  private def resolveAppeal(
      connection: java.sql.Connection,
      module: TournamentAppealModuleContext,
      command: ResolveAppealCommand
  ): AppealTicket =
    module.service.resolveAppeal(
      connection = connection,
      ticketId = command.ticketId,
      verdict = command.input.verdict,
      actor = command.actor,
      resolvedAt = command.resolvedAt,
      note = command.input.note
    ).getOrElse(throw NoSuchElementException("Resource not found"))

  private final case class ResolveAppealCommand(
      ticketId: AppealTicketId,
      input: ResolveAppealRequest,
      actor: AccessPrincipal,
      resolvedAt: Instant
  )
