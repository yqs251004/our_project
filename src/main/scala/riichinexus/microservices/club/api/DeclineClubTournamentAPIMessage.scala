package riichinexus.microservices.club.api
import riichinexus.microservices.auth.api.`private`.AuthAccessPrincipalResolver

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
import riichinexus.microservices.club.domain.Club
import riichinexus.microservices.club.domain.clubmanagement.model.*
import riichinexus.microservices.club.domain.membershipmanagement.model.*
import riichinexus.microservices.club.domain.rankprivilegemanagement.model.*
import riichinexus.microservices.club.domain.relationmanagement.model.*
import riichinexus.microservices.club.objects.rankprivilegemanagement.ClubPrivilegeCode
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.club.domain.ClubAuthorization
import riichinexus.microservices.tournament.api.`private`.DeclineClubTournamentPrivateAPIMessage
import riichinexus.microservices.tournament.api.`private`.TournamentOperationViewAssembler
import riichinexus.microservices.tournament.objects.lineupmanagement.apiTypes.*
import riichinexus.microservices.tournament.objects.paifumanagement.apiTypes.*
import riichinexus.microservices.tournament.objects.recordmanagement.apiTypes.*
import riichinexus.microservices.tournament.objects.rulesmanagement.apiTypes.*
import riichinexus.microservices.tournament.objects.settlementmanagement.apiTypes.*
import riichinexus.microservices.tournament.objects.tablemanagement.apiTypes.*
import riichinexus.microservices.tournament.objects.tournamentmanagement.apiTypes.*
import upickle.default.*

final case class DeclineClubTournamentAPIMessage(
    clubId: String,
    tournamentId: String,
    operatorId: Option[String] = None
) extends APIMessage[TournamentMutationView] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[TournamentMutationView] =
    for
      actor <- IO.blocking(resolveOperatorActor(context))
      module = context.support.clubModule.tournamentModule
      command = DeclineClubTournamentCommand(
        clubId = ClubId(clubId),
        tournamentId = TournamentId(tournamentId),
        actor = actor
      )
      _ <- IO.blocking {
        module.transactionManager.inTransaction {
          declineTournament(context.connection, module, command)
        }
      }
      view <- IO.blocking {
        TournamentOperationViewAssembler.mutationView(context.connection, module, command.tournamentId, Vector.empty)
        .getOrElse(throw NoSuchElementException("Resource not found"))
      }
    yield view

  private def resolveOperatorActor(context: ApiPlanContext): AccessPrincipal =
    operatorId.filter(_.nonEmpty)
      .map(id => AuthAccessPrincipalResolver.principal(context, PlayerId(id)))
      .getOrElse(throw IllegalArgumentException("operatorId is required"))

  private def declineTournament(
      connection: java.sql.Connection,
      module: TournamentModuleContext,
      command: DeclineClubTournamentCommand
  ): Unit =
    val club = resolveActiveClub(connection, command.clubId)
    requireClubLineupCapability(module, command.actor, club)
    DeclineClubTournamentPrivateAPIMessage(command.tournamentId, command.clubId)
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
      delegatedPrivileges = Set(ClubPrivilegeCode.PriorityLineup)
    )

  private final case class DeclineClubTournamentCommand(
      clubId: ClubId,
      tournamentId: TournamentId,
      actor: AccessPrincipal
  )
