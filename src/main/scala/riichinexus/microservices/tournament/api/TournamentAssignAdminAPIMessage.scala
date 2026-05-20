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

final case class TournamentAssignAdminAPIMessage(tournamentId: String, request: AssignTournamentAdminRequest) extends APIMessage[TournamentSummaryView] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[TournamentSummaryView] =
    IO {
      val module = context.support.tournamentModule
      val tournamentIdValue = TournamentId(tournamentId)
      val playerIdValue = request.player
      val actor = context.support.principal(request.operator)
      val grantedAt = Instant.now()

      module.transactionManager.inTransaction {
        for
          tournament <- module.tournamentRepository.findById(tournamentIdValue)
          player <- module.playerRepository.findById(playerIdValue)
        yield
          if player.status != PlayerStatus.Active then
            throw IllegalArgumentException(s"Player ${playerIdValue.value} cannot be granted tournament admin")
          module.authorizationService.requirePermission(
            actor,
            Permission.AssignTournamentAdmin,
            tournamentId = Some(tournamentIdValue)
          )

          module.playerRepository.save(
            player.grantRole(
              RoleGrant.tournamentAdmin(tournamentIdValue, grantedAt, actor.playerId)
            )
          )
          val updatedTournament = module.tournamentRepository.save(tournament.assignAdmin(playerIdValue))
          module.auditEventRepository.save(
            AuditEventEntry(
              id = IdGenerator.auditEventId(),
              aggregateType = "tournament",
              aggregateId = tournamentIdValue.value,
              eventType = "TournamentAdminAssigned",
              occurredAt = grantedAt,
              actorId = actor.playerId,
              details = Map("playerId" -> playerIdValue.value),
              note = Some(s"Granted tournament admin to ${playerIdValue.value}")
            )
          )
          TournamentSummaryView.fromDomain(updatedTournament)
      }.getOrElse(throw NoSuchElementException("Resource not found"))
    }
