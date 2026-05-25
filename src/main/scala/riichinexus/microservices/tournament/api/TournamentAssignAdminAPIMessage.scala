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

final case class TournamentAssignAdminAPIMessage(tournamentId: String, request: AssignTournamentAdminRequest) extends APIMessage[TournamentSummaryView] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[TournamentSummaryView] =
    for
      actor <- IO(context.support.principal(request.operator))
      grantedAt <- IO.realTimeInstant
      module = context.support.tournamentModule
      command = AssignTournamentAdminCommand(
        tournamentId = TournamentId(tournamentId),
        playerId = request.player,
        actor = actor,
        grantedAt = grantedAt
      )
      tournament <- IO {
        module.transactionManager.inTransaction {
          assignAdmin(module, command)
        }.getOrElse(throw NoSuchElementException("Resource not found"))
      }
    yield TournamentSummaryView.fromDomain(tournament)

  private def assignAdmin(
      module: TournamentModuleContext,
      command: AssignTournamentAdminCommand
  ): Option[Tournament] =
    for
      tournament <- module.tournamentRepository.findById(command.tournamentId)
      player <- module.playerRepository.findById(command.playerId)
    yield
      ensureAdminCanBeAssigned(module, player, command)
      commitAdminAssignment(module, tournament, player, command)

  private def ensureAdminCanBeAssigned(
      module: TournamentModuleContext,
      player: Player,
      command: AssignTournamentAdminCommand
  ): Unit =
    if player.status != PlayerStatus.Active then
      throw IllegalArgumentException(s"Player ${command.playerId.value} cannot be granted tournament admin")
    module.authorizationService.requirePermission(
      command.actor,
      Permission.AssignTournamentAdmin,
      tournamentId = Some(command.tournamentId)
    )

  private def commitAdminAssignment(
      module: TournamentModuleContext,
      tournament: Tournament,
      player: Player,
      command: AssignTournamentAdminCommand
  ): Tournament =
    DomainChangeInterpreter
      .auditOnly(module.transactionManager, module.auditEventRepository)
      .commitAudited(
        aggregate = tournament.assignAdmin(command.playerId),
        persist = nextTournament =>
          module.playerRepository.save(
            player.grantRole(
              RoleGrant.tournamentAdmin(command.tournamentId, command.grantedAt, command.actor.playerId)
            )
          )
          module.tournamentRepository.save(nextTournament),
        aggregateType = "tournament",
        aggregateId = _.id.value,
        eventType = "TournamentAdminAssigned",
        occurredAt = command.grantedAt,
        actorId = command.actor.playerId,
        details = _ => Map("playerId" -> command.playerId.value),
        note = Some(s"Granted tournament admin to ${command.playerId.value}")
      )

  private final case class AssignTournamentAdminCommand(
      tournamentId: TournamentId,
      playerId: PlayerId,
      actor: AccessPrincipal,
      grantedAt: Instant
  )
