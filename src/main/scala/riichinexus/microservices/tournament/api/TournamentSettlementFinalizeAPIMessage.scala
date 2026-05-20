package riichinexus.microservices.tournament.api

import java.util.NoSuchElementException
import java.time.Instant

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.tournament.objects.*
import riichinexus.microservices.tournament.objects.apiTypes.*
import riichinexus.microservices.tournament.objects.apiTypes.ManagementRequests.given
import riichinexus.microservices.tournament.objects.apiTypes.SettlementRequests.given
import riichinexus.microservices.tournament.objects.apiTypes.StageRequests.given
import riichinexus.microservices.tournament.objects.apiTypes.TableRequests.given
import upickle.default.*

final case class TournamentSettlementFinalizeAPIMessage(tournamentId: String, settlementId: String, request: FinalizeTournamentSettlementRequest) extends APIMessage[TournamentSettlementView] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[TournamentSettlementView] =
    IO {
      val module = context.support.tournamentModule
      val tournamentIdValue = TournamentId(tournamentId)
      val settlementIdValue = SettlementSnapshotId(settlementId)
      val actor = context.support.principal(request.operator)
      val finalizedAt = Instant.now()

      module.transactionManager.inTransaction {
        module.authorizationService.requirePermission(
          actor,
          Permission.ManageTournamentStages,
          tournamentId = Some(tournamentIdValue)
        )

        module.tournamentSettlementRepository.findById(settlementIdValue)
          .filter(_.tournamentId == tournamentIdValue)
          .map { settlement =>
            val finalized =
              if settlement.status == TournamentSettlementStatus.Finalized then settlement
              else module.tournamentSettlementRepository.save(settlement.finalize(finalizedAt))
            module.auditEventRepository.save(
              AuditEventEntry(
                id = IdGenerator.auditEventId(),
                aggregateType = "tournament",
                aggregateId = tournamentIdValue.value,
                eventType = "TournamentSettlementFinalized",
                occurredAt = finalizedAt,
                actorId = actor.playerId,
                details = Map(
                  "stageId" -> finalized.stageId.value,
                  "settlementId" -> finalized.id.value,
                  "revision" -> finalized.revision.toString
                ),
                note = request.note.orElse(Some(s"Finalized settlement ${finalized.id.value}"))
              )
            )
            TournamentSettlementView.fromDomain(finalized)
          }
      }.getOrElse(throw NoSuchElementException("Resource not found"))
    }
