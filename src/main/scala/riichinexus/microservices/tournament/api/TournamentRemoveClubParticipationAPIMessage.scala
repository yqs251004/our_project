package riichinexus.microservices.tournament.api

import java.util.NoSuchElementException

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.bootstrap.TournamentModuleContext
import riichinexus.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.tournament.domain.TournamentOperationViewAssembler
import riichinexus.microservices.tournament.objects.*
import riichinexus.microservices.tournament.objects.apiTypes.{Table as _, TableSeat as _, StageStandingEntry as _, StageRankingSnapshot as _, StageAdvancementSnapshot as _, KnockoutBracketSlot as _, KnockoutBracketResult as _, KnockoutBracketMatch as _, KnockoutBracketRound as _, KnockoutBracketSnapshot as _, *}
import riichinexus.microservices.tournament.objects.apiTypes.ManagementRequests.given
import riichinexus.microservices.tournament.objects.apiTypes.SettlementRequests.given
import riichinexus.microservices.tournament.objects.apiTypes.StageRequests.given
import riichinexus.microservices.tournament.objects.apiTypes.TableRequests.given
import riichinexus.microservices.tournament.objects.apiTypes.OperatorRequest
import upickle.default.*

final case class TournamentRemoveClubParticipationAPIMessage(tournamentId: String, clubId: String, operatorId: Option[String] = None) extends APIMessage[TournamentMutationView] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[TournamentMutationView] =
    for
      actor <- IO(resolveOperatorActor(context))
      module = context.support.tournamentModule
      command = RemoveClubParticipationCommand(TournamentId(tournamentId), ClubId(clubId), actor)
      _ <- IO {
        module.transactionManager.inTransaction {
          removeClubParticipation(module, command)
        }
      }
      view <- IO {
        TournamentOperationViewAssembler.mutationView(module, command.tournamentId, Vector.empty)
        .getOrElse(throw NoSuchElementException("Resource not found"))
      }
    yield view

  private def resolveOperatorActor(context: ApiPlanContext): AccessPrincipal =
    OperatorRequest(operatorId.filter(_.nonEmpty)).operator
      .map(context.support.principal)
      .getOrElse(AccessPrincipal.system)

  private def removeClubParticipation(
      module: TournamentModuleContext,
      command: RemoveClubParticipationCommand
  ): Unit =
    module.authorizationService.requirePermission(
      command.actor,
      Permission.ManageTournamentStages,
      tournamentId = Some(command.tournamentId)
    )
    module.clubRepository
      .findById(command.clubId)
      .getOrElse(throw NoSuchElementException(s"Club ${command.clubId.value} was not found"))
    module.tournamentRepository.findById(command.tournamentId).foreach { tournament =>
      ensureClubTracked(tournament, command)
      module.tournamentRepository.save(tournament.removeClub(command.clubId))
    }

  private def ensureClubTracked(
      tournament: Tournament,
      command: RemoveClubParticipationCommand
  ): Unit =
    val trackedParticipation =
      tournament.participatingClubs.contains(command.clubId) ||
        tournament.whitelist.exists(_.clubId.contains(command.clubId))
    if !trackedParticipation then
      throw IllegalArgumentException(
        s"Club ${command.clubId.value} is not participating in tournament ${command.tournamentId.value}"
      )

  private final case class RemoveClubParticipationCommand(
      tournamentId: TournamentId,
      clubId: ClubId,
      actor: AccessPrincipal
  )
