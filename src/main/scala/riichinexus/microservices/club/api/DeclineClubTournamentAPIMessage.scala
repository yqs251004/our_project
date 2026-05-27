package riichinexus.microservices.club.api

import java.util.NoSuchElementException

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.bootstrap.TournamentModuleContext
import riichinexus.domain.model.*
import riichinexus.microservices.auth.domain.model.*
import riichinexus.microservices.tournament.domain.model.*
import riichinexus.microservices.club.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.club.domain.ClubAuthorization
import riichinexus.microservices.tournament.domain.TournamentOperationViewAssembler
import riichinexus.microservices.tournament.objects.apiTypes.*
import upickle.default.*

final case class DeclineClubTournamentAPIMessage(
    clubId: String,
    tournamentId: String,
    operatorId: Option[String] = None
) extends APIMessage[TournamentMutationView] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[TournamentMutationView] =
    for
      actor <- IO(resolveOperatorActor(context))
      module = context.support.clubModule.tournamentModule
      command = DeclineClubTournamentCommand(
        clubId = ClubId(clubId),
        tournamentId = TournamentId(tournamentId),
        actor = actor
      )
      _ <- IO {
        module.transactionManager.inTransaction {
          declineTournament(context.connection, module, command)
        }
      }
      view <- IO {
        TournamentOperationViewAssembler.mutationView(context.connection, module, command.tournamentId, Vector.empty)
        .getOrElse(throw NoSuchElementException("Resource not found"))
      }
    yield view

  private def resolveOperatorActor(context: ApiPlanContext): AccessPrincipal =
    operatorId.filter(_.nonEmpty)
      .map(id => context.principal(PlayerId(id)))
      .getOrElse(throw IllegalArgumentException("operatorId is required"))

  private def declineTournament(
      connection: java.sql.Connection,
      module: TournamentModuleContext,
      command: DeclineClubTournamentCommand
  ): Unit =
    val club = resolveActiveClub(connection, command.clubId)
    requireClubLineupCapability(module, command.actor, club)
    riichinexus.microservices.tournament.tables.tournament.TournamentTable.findById(connection, command.tournamentId).foreach { tournament =>
      ensureClubTracked(tournament, command)
      riichinexus.microservices.tournament.tables.tournament.TournamentTable.save(connection, tournament.removeClub(command.clubId))
    }

  private def resolveActiveClub(connection: java.sql.Connection, clubId: ClubId): Club =
    riichinexus.microservices.club.tables.club.ClubTable
      .findById(connection, clubId)
      .map { club =>
        ClubAuthorization.ensureClubActive(club)
        club
      }
      .getOrElse(throw NoSuchElementException(s"Club ${clubId.value} was not found"))

  private def ensureClubTracked(
      tournament: Tournament,
      command: DeclineClubTournamentCommand
  ): Unit =
    val trackedParticipation =
      tournament.participatingClubs.contains(command.clubId) ||
        tournament.whitelist.exists(_.clubId.contains(command.clubId))
    if !trackedParticipation then
      throw IllegalArgumentException(
        s"Club ${command.clubId.value} is not participating in tournament ${command.tournamentId.value}"
      )

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

  private final case class DeclineClubTournamentCommand(
      clubId: ClubId,
      tournamentId: TournamentId,
      actor: AccessPrincipal
  )
