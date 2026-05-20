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

final case class TournamentStartAPIMessage(tournamentId: String, operatorId: Option[String] = None) extends APIMessage[TournamentSummaryView] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[TournamentSummaryView] =
    IO {
      val module = context.support.tournamentModule
      val tournamentIdValue = TournamentId(tournamentId)
      val actor = OperatorRequest(operatorId.filter(_.nonEmpty)).operator
        .map(context.support.principal)
        .getOrElse(AccessPrincipal.system)

      module.transactionManager.inTransaction {
        module.tournamentRepository.findById(tournamentIdValue).map { tournament =>
          module.authorizationService.requirePermission(
            actor,
            Permission.ManageTournamentStages,
            tournamentId = Some(tournamentIdValue)
          )

          if tournament.participatingPlayers.isEmpty && tournament.participatingClubs.isEmpty then
            throw IllegalArgumentException(
              s"Tournament ${tournamentIdValue.value} cannot start without participants"
            )
          TournamentSummaryView.fromDomain(module.tournamentRepository.save(tournament.start))
        }
      }.getOrElse(throw NoSuchElementException("Resource not found"))
    }
