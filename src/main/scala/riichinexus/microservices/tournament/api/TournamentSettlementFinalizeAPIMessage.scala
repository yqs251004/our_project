package riichinexus.microservices.tournament.api

import java.util.NoSuchElementException
import java.time.Instant

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.application.changes.DomainChangeInterpreter
import riichinexus.bootstrap.TournamentModuleContext
import riichinexus.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.tournament.objects.*
import riichinexus.microservices.tournament.objects.apiTypes.{Table as _, TableSeat as _, StageStandingEntry as _, StageRankingSnapshot as _, StageAdvancementSnapshot as _, KnockoutBracketSlot as _, KnockoutBracketResult as _, KnockoutBracketMatch as _, KnockoutBracketRound as _, KnockoutBracketSnapshot as _, *}
import riichinexus.microservices.tournament.objects.apiTypes.ManagementRequests.given
import riichinexus.microservices.tournament.objects.apiTypes.SettlementRequests.given
import riichinexus.microservices.tournament.objects.apiTypes.StageRequests.given
import riichinexus.microservices.tournament.objects.apiTypes.TableRequests.given
import upickle.default.*

final case class TournamentSettlementFinalizeAPIMessage(tournamentId: String, settlementId: String, request: FinalizeTournamentSettlementRequest) extends APIMessage[TournamentSettlementView] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[TournamentSettlementView] =
    for
      actor <- IO(context.support.principal(request.operator))
      finalizedAt <- IO.realTimeInstant
      module = context.support.tournamentModule
      command = FinalizeSettlementCommand(
        tournamentId = TournamentId(tournamentId),
        settlementId = SettlementSnapshotId(settlementId),
        actor = actor,
        note = request.note,
        finalizedAt = finalizedAt
      )
      settlement <- IO {
        module.transactionManager.inTransaction {
          finalizeSettlement(module, command)
        }.getOrElse(throw NoSuchElementException("Resource not found"))
      }
    yield TournamentSettlementView.fromDomain(settlement)

  private def finalizeSettlement(
      module: TournamentModuleContext,
      command: FinalizeSettlementCommand
  ): Option[TournamentSettlementSnapshot] =
    requireFinalizePermission(module, command)
    module.tournamentSettlementRepository
      .findById(command.settlementId)
      .filter(_.tournamentId == command.tournamentId)
      .map(settlement => commitFinalizedSettlement(module, command, settlement))

  private def requireFinalizePermission(
      module: TournamentModuleContext,
      command: FinalizeSettlementCommand
  ): Unit =
    module.authorizationService.requirePermission(
      command.actor,
      Permission.ManageTournamentStages,
      tournamentId = Some(command.tournamentId)
    )

  private def commitFinalizedSettlement(
      module: TournamentModuleContext,
      command: FinalizeSettlementCommand,
      settlement: TournamentSettlementSnapshot
  ): TournamentSettlementSnapshot =
    DomainChangeInterpreter
      .auditOnly(module.transactionManager, module.auditEventRepository)
      .commitAudited(
        aggregate =
          if settlement.status == TournamentSettlementStatus.Finalized then settlement
          else settlement.finalize(command.finalizedAt),
        persist = finalized =>
          if settlement.status == TournamentSettlementStatus.Finalized then finalized
          else module.tournamentSettlementRepository.save(finalized),
        aggregateType = "tournament",
        aggregateId = _.tournamentId.value,
        eventType = "TournamentSettlementFinalized",
        occurredAt = command.finalizedAt,
        actorId = command.actor.playerId,
        details = finalized =>
          Map(
            "stageId" -> finalized.stageId.value,
            "settlementId" -> finalized.id.value,
            "revision" -> finalized.revision.toString
          ),
        note = command.note.orElse(Some(s"Finalized settlement ${settlement.id.value}"))
      )

  private final case class FinalizeSettlementCommand(
      tournamentId: TournamentId,
      settlementId: SettlementSnapshotId,
      actor: AccessPrincipal,
      note: Option[String],
      finalizedAt: Instant
  )
