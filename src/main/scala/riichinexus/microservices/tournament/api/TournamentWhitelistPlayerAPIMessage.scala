package riichinexus.microservices.tournament.api

import java.util.NoSuchElementException

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
import riichinexus.microservices.tournament.objects.apiTypes.OperatorRequest
import upickle.default.*

final case class TournamentWhitelistPlayerAPIMessage(tournamentId: String, playerId: String, operatorId: Option[String] = None) extends APIMessage[TournamentSummaryView] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[TournamentSummaryView] =
    IO {
      val module = context.support.tournamentModule
      val tournamentIdValue = TournamentId(tournamentId)
      val playerIdValue = PlayerId(playerId)
      val actor = OperatorRequest(operatorId).operator
        .map(context.support.principal)
        .getOrElse(AccessPrincipal.system)

      module.transactionManager.inTransaction {
        module.authorizationService.requirePermission(
          actor,
          Permission.ManageTournamentStages,
          tournamentId = Some(tournamentIdValue)
        )

        val player = module.playerRepository
          .findById(playerIdValue)
          .getOrElse(throw NoSuchElementException(s"Player ${playerIdValue.value} was not found"))
        if player.status != PlayerStatus.Active then
          throw IllegalArgumentException(s"Player ${playerIdValue.value} cannot be whitelisted")

        module.tournamentRepository.findById(tournamentIdValue).map { tournament =>
          TournamentSummaryView.fromDomain(module.tournamentRepository.save(tournament.whitelistPlayer(playerIdValue)))
        }
      }.getOrElse(throw NoSuchElementException("Resource not found"))
    }
