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
import riichinexus.system.objects.apiTypes.OperatorRequest
import upickle.default.*

final case class TournamentWhitelistClubAPIMessage(tournamentId: String, clubId: String, operatorId: Option[String] = None) extends APIMessage[TournamentSummaryView] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[TournamentSummaryView] =
    IO {
      val module = context.support.tournamentModule
      val tournamentIdValue = TournamentId(tournamentId)
      val clubIdValue = ClubId(clubId)
      val actor = OperatorRequest(operatorId).operator
        .map(context.support.principal)
        .getOrElse(AccessPrincipal.system)

      module.transactionManager.inTransaction {
        module.authorizationService.requirePermission(
          actor,
          Permission.ManageTournamentStages,
          tournamentId = Some(tournamentIdValue)
        )

        val club = module.clubRepository
          .findById(clubIdValue)
          .getOrElse(throw NoSuchElementException(s"Club ${clubIdValue.value} was not found"))
        ensureClubActive(club)

        module.tournamentRepository.findById(tournamentIdValue).map { tournament =>
          TournamentSummaryView.fromDomain(module.tournamentRepository.save(tournament.whitelistClub(clubIdValue)))
        }
      }.getOrElse(throw NoSuchElementException("Resource not found"))
    }

  private def ensureClubActive(club: Club): Unit =
    if club.dissolvedAt.nonEmpty then
      throw IllegalArgumentException(s"Club ${club.id.value} has already been dissolved")
