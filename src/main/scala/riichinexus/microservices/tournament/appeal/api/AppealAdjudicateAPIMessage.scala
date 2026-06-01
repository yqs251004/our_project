package riichinexus.microservices.tournament.appeal.api
import riichinexus.microservices.auth.api.`private`.AuthAccessPrincipalResolver

import java.time.Instant
import java.util.NoSuchElementException

import cats.effect.IO

import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.bootstrap.TournamentAppealModuleContext
import riichinexus.domain.model.*
import riichinexus.microservices.auth.domain.model.*
import riichinexus.microservices.tournament.appeal.domain.model.{
  AppealDecisionType as DomainAppealDecisionType,
  AppealTableResolution as DomainAppealTableResolution,
  AppealTicket
}
import riichinexus.microservices.tournament.domain.lineupmanagement.model.*
import riichinexus.microservices.tournament.domain.recordmanagement.model.*
import riichinexus.microservices.tournament.domain.settlementmanagement.model.*
import riichinexus.microservices.tournament.domain.tablemanagement.model.*
import riichinexus.microservices.tournament.domain.tournamentmanagement.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.tournament.appeal.objects.apiTypes.*
import upickle.default.*

final case class AppealAdjudicateAPIMessage(
    appealId: String,
    request: AdjudicateAppealRequest
) extends APIMessage[AppealTicketView] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[AppealTicketView] =
    for
      actor <- IO.blocking(AuthAccessPrincipalResolver.principal(context, PlayerId(request.operatorId)))
      adjudicatedAt <- IO.realTimeInstant
      module = context.support.tournamentAppealModule
      command <- IO.blocking(resolveCommand(actor, adjudicatedAt))
      ticket <- IO.blocking(adjudicateAppeal(context.connection, module, command))
    yield AppealTicketView.fromDomain(ticket)

  private def resolveCommand(actor: AccessPrincipal, adjudicatedAt: Instant): AdjudicateAppealCommand =
    AdjudicateAppealCommand(
      ticketId = AppealTicketId(appealId),
      decision = request.decision.toDomain,
      verdict = request.verdict,
      actor = actor,
      tableResolution = request.tableResolution.map(_.toDomain),
      note = request.note,
      adjudicatedAt = adjudicatedAt
    )

  private def adjudicateAppeal(
      connection: java.sql.Connection,
      module: TournamentAppealModuleContext,
      command: AdjudicateAppealCommand
  ): AppealTicket =
    module.service.adjudicateAppeal(
      connection = connection,
      ticketId = command.ticketId,
      decision = command.decision,
      verdict = command.verdict,
      actor = command.actor,
      adjudicatedAt = command.adjudicatedAt,
      tableResolution = command.tableResolution,
      note = command.note
    ).getOrElse(throw NoSuchElementException("Resource not found"))

  private final case class AdjudicateAppealCommand(
      ticketId: AppealTicketId,
      decision: DomainAppealDecisionType,
      verdict: String,
      actor: AccessPrincipal,
      tableResolution: Option[DomainAppealTableResolution],
      note: Option[String],
      adjudicatedAt: Instant
  )
