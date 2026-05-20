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
import riichinexus.system.objects.apiTypes.OperatorRequest
import upickle.default.*

final case class TournamentRevokeAdminAPIMessage(tournamentId: String, playerId: String, operatorId: Option[String] = None) extends APIMessage[TournamentSummaryView] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[TournamentSummaryView] =
    IO {
      val module = context.support.tournamentModule
      val tournamentIdValue = TournamentId(tournamentId)
      val playerIdValue = PlayerId(playerId)
      val actor = OperatorRequest(operatorId).operator
        .map(context.support.principal)
        .getOrElse(AccessPrincipal.system)

      module.transactionManager.inTransaction {
        for
          tournament <- module.tournamentRepository.findById(tournamentIdValue)
          player <- module.playerRepository.findById(playerIdValue)
        yield
          module.authorizationService.requirePermission(
            actor,
            Permission.AssignTournamentAdmin,
            tournamentId = Some(tournamentIdValue)
          )

          if !tournament.admins.contains(playerIdValue) then
            throw IllegalArgumentException(
              s"Player ${playerIdValue.value} is not a tournament admin of tournament ${tournamentIdValue.value}"
            )

          if tournament.admins.size <= 1 then
            throw IllegalArgumentException(
              s"Tournament ${tournamentIdValue.value} must retain at least one tournament admin"
            )

          module.playerRepository.save(player.revokeTournamentAdmin(tournamentIdValue))
          val updatedTournament = module.tournamentRepository.save(
            tournament.copy(admins = tournament.admins.filterNot(_ == playerIdValue))
          )
          module.auditEventRepository.save(
            AuditEventEntry(
              id = IdGenerator.auditEventId(),
              aggregateType = "tournament",
              aggregateId = tournamentIdValue.value,
              eventType = "TournamentAdminRevoked",
              occurredAt = Instant.now(),
              actorId = actor.playerId,
              details = Map("playerId" -> playerIdValue.value),
              note = Some(s"Revoked tournament admin from ${playerIdValue.value}")
            )
          )
          TournamentSummaryView.fromDomain(updatedTournament)
      }.getOrElse(throw NoSuchElementException("Resource not found"))
    }
