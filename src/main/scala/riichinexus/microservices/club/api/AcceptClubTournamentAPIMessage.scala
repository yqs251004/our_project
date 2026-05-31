package riichinexus.microservices.club.api

import java.util.NoSuchElementException

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.bootstrap.TournamentModuleContext
import riichinexus.domain.model.*
import riichinexus.microservices.auth.domain.model.*
import riichinexus.microservices.tournament.domain.lineupmanagement.model.*
import riichinexus.microservices.tournament.domain.recordmanagement.model.*
import riichinexus.microservices.tournament.domain.settlementmanagement.model.*
import riichinexus.microservices.tournament.domain.tablemanagement.model.*
import riichinexus.microservices.tournament.domain.tournamentmanagement.model.*
import riichinexus.microservices.club.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.club.domain.ClubAuthorization
import riichinexus.microservices.tournament.api.`private`.AcceptClubTournamentPrivateAPIMessage
import riichinexus.microservices.tournament.api.`private`.TournamentOperationViewAssembler
import riichinexus.microservices.tournament.objects.lineupmanagement.apiTypes.*
import riichinexus.microservices.tournament.objects.paifumanagement.apiTypes.*
import riichinexus.microservices.tournament.objects.recordmanagement.apiTypes.*
import riichinexus.microservices.tournament.objects.rulesmanagement.apiTypes.*
import riichinexus.microservices.tournament.objects.rulesmanagement.ranking.apiTypes.*
import riichinexus.microservices.tournament.objects.settlementmanagement.apiTypes.*
import riichinexus.microservices.tournament.objects.tablemanagement.apiTypes.*
import riichinexus.microservices.tournament.objects.tournamentmanagement.apiTypes.*
import upickle.default.*

final case class AcceptClubTournamentAPIMessage(
    clubId: String,
    tournamentId: String,
    operatorId: Option[String] = None
) extends APIMessage[TournamentMutationView] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[TournamentMutationView] =
    for
      actor <- IO.blocking(resolveOperatorActor(context))
      module = context.support.clubModule.tournamentModule
      command = AcceptClubTournamentCommand(
        clubId = ClubId(clubId),
        tournamentId = TournamentId(tournamentId),
        actor = actor
      )
      _ <- IO.blocking {
        module.transactionManager.inTransaction {
          acceptTournament(context.connection, module, command)
        }
      }
      view <- IO.blocking {
        TournamentOperationViewAssembler.mutationView(context.connection, module, command.tournamentId, Vector.empty)
        .getOrElse(throw NoSuchElementException("Resource not found"))
      }
    yield view

  private def resolveOperatorActor(context: ApiPlanContext): AccessPrincipal =
    operatorId.filter(_.nonEmpty)
      .map(id => context.principal(PlayerId(id)))
      .getOrElse(throw IllegalArgumentException("operatorId is required"))

  private def acceptTournament(
      connection: java.sql.Connection,
      module: TournamentModuleContext,
      command: AcceptClubTournamentCommand
  ): Unit =
    val club = resolveActiveClub(connection, command.clubId)
    requireClubLineupCapability(module, command.actor, club)
    AcceptClubTournamentPrivateAPIMessage(command.tournamentId, command.clubId)
      .plan(ApiPlanContext(support = null, bearerToken = None, connection = connection))
      .unsafeRunSync()

  private def resolveActiveClub(connection: java.sql.Connection, clubId: ClubId): Club =
    riichinexus.microservices.club.tables.clubs.ClubTable
      .findById(connection, clubId)
      .map { club =>
        ClubAuthorization.ensureClubActive(club)
        club
      }
      .getOrElse(throw NoSuchElementException(s"Club ${clubId.value} was not found"))

  private def requireClubLineupCapability(
      module: TournamentModuleContext,
      actor: AccessPrincipal,
      club: Club
  ): Unit =
    ClubAuthorization.requireClubCapability(
      authorizationService = module.authorizationService,
      actor = actor,
      club = club,
      permission = Permission.SubmitTournamentLineup,
      delegatedPrivileges = Set(ClubPrivilege.PriorityLineup)
    )

  private final case class AcceptClubTournamentCommand(
      clubId: ClubId,
      tournamentId: TournamentId,
      actor: AccessPrincipal
  )
