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
import riichinexus.microservices.tournament.objects.apiTypes.OperatorRequest
import upickle.default.*

final case class TournamentRevokeAdminAPIMessage(tournamentId: String, playerId: String, operatorId: Option[String] = None) extends APIMessage[TournamentSummaryView] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[TournamentSummaryView] =
    for
      actor <- IO(resolveOperatorActor(context))
      revokedAt <- IO.realTimeInstant
      module = context.support.tournamentModule
      command = RevokeTournamentAdminCommand(
        tournamentId = TournamentId(tournamentId),
        playerId = PlayerId(playerId),
        actor = actor,
        revokedAt = revokedAt
      )
      tournament <- IO {
        module.transactionManager.inTransaction {
          revokeAdmin(module, command)
        }.getOrElse(throw NoSuchElementException("Resource not found"))
      }
    yield TournamentSummaryView.fromDomain(tournament)

  private def resolveOperatorActor(context: ApiPlanContext): AccessPrincipal =
    OperatorRequest(operatorId).operator
      .map(context.support.principal)
      .getOrElse(AccessPrincipal.system)

  private def revokeAdmin(
      module: TournamentModuleContext,
      command: RevokeTournamentAdminCommand
  ): Option[Tournament] =
    for
      tournament <- module.tournamentRepository.findById(command.tournamentId)
      player <- module.playerRepository.findById(command.playerId)
    yield
      ensureAdminCanBeRevoked(module, tournament, command)
      commitAdminRevocation(module, tournament, player, command)

  private def ensureAdminCanBeRevoked(
      module: TournamentModuleContext,
      tournament: Tournament,
      command: RevokeTournamentAdminCommand
  ): Unit =
    module.authorizationService.requirePermission(
      command.actor,
      Permission.AssignTournamentAdmin,
      tournamentId = Some(command.tournamentId)
    )
    if !tournament.admins.contains(command.playerId) then
      throw IllegalArgumentException(
        s"Player ${command.playerId.value} is not a tournament admin of tournament ${command.tournamentId.value}"
      )
    if tournament.admins.size <= 1 then
      throw IllegalArgumentException(
        s"Tournament ${command.tournamentId.value} must retain at least one tournament admin"
      )

  private def commitAdminRevocation(
      module: TournamentModuleContext,
      tournament: Tournament,
      player: Player,
      command: RevokeTournamentAdminCommand
  ): Tournament =
    DomainChangeInterpreter
      .auditOnly(module.transactionManager, module.auditEventRepository)
      .commitAudited(
        aggregate = tournament.copy(admins = tournament.admins.filterNot(_ == command.playerId)),
        persist = nextTournament =>
          module.playerRepository.save(player.revokeTournamentAdmin(command.tournamentId))
          module.tournamentRepository.save(nextTournament),
        aggregateType = "tournament",
        aggregateId = _.id.value,
        eventType = "TournamentAdminRevoked",
        occurredAt = command.revokedAt,
        actorId = command.actor.playerId,
        details = _ => Map("playerId" -> command.playerId.value),
        note = Some(s"Revoked tournament admin from ${command.playerId.value}")
      )

  private final case class RevokeTournamentAdminCommand(
      tournamentId: TournamentId,
      playerId: PlayerId,
      actor: AccessPrincipal,
      revokedAt: Instant
  )
