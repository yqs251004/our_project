package riichinexus.microservices.club.api

import java.util.NoSuchElementException

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.bootstrap.TournamentModuleContext
import riichinexus.domain.model.*
import riichinexus.domain.service.AuthorizationFailure
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.tournament.domain.TournamentOperationViewAssembler
import riichinexus.microservices.tournament.objects.apiTypes.*
import riichinexus.microservices.tournament.objects.apiTypes.TournamentOperationResponses.given
import upickle.default.*

final case class AcceptClubTournamentAPIMessage(
    clubId: String,
    tournamentId: String,
    operatorId: Option[String] = None
) extends APIMessage[TournamentMutationView] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[TournamentMutationView] =
    for
      actor <- IO(resolveOperatorActor(context))
      module = context.support.clubModule.tournamentModule
      command = AcceptClubTournamentCommand(
        clubId = ClubId(clubId),
        tournamentId = TournamentId(tournamentId),
        actor = actor
      )
      _ <- IO {
        module.transactionManager.inTransaction {
          acceptTournament(module, command)
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

  private def acceptTournament(
      module: TournamentModuleContext,
      command: AcceptClubTournamentCommand
  ): Unit =
    val club = resolveActiveClub(module, command.clubId)
    requireClubLineupCapability(module, command.actor, club)
    module.tournamentRepository.findById(command.tournamentId).foreach { tournament =>
      ensureClubInvitedOrParticipating(tournament, command)
      module.tournamentRepository.save(tournament.registerClub(command.clubId))
    }

  private def resolveActiveClub(module: TournamentModuleContext, clubId: ClubId): Club =
    module.clubRepository
      .findById(clubId)
      .map { club =>
        ensureClubActive(club)
        club
      }
      .getOrElse(throw NoSuchElementException(s"Club ${clubId.value} was not found"))

  private def ensureClubInvitedOrParticipating(
      tournament: Tournament,
      command: AcceptClubTournamentCommand
  ): Unit =
    val alreadyParticipating = tournament.participatingClubs.contains(command.clubId)
    val isWhitelisted = tournament.whitelist.exists(_.clubId.contains(command.clubId))
    if !alreadyParticipating && !isWhitelisted then
      throw IllegalArgumentException(
        s"Club ${command.clubId.value} is not invited to tournament ${command.tournamentId.value}"
      )

  private def ensureClubActive(club: Club): Unit =
    if club.dissolvedAt.nonEmpty then
      throw IllegalArgumentException(s"Club ${club.id.value} has already been dissolved")

  private def requireClubLineupCapability(
      module: TournamentModuleContext,
      actor: AccessPrincipal,
      club: Club
  ): Unit =
    val hasBasePermission =
      module.authorizationService.can(
        actor,
        Permission.SubmitTournamentLineup,
        clubId = Some(club.id)
      )
    val hasDelegatedPrivilege = actor.playerId.exists { playerId =>
      club.members.contains(playerId) && club.hasPrivilege(playerId, ClubPrivilege.PriorityLineup)
    }

    if !hasBasePermission && !hasDelegatedPrivilege then
      throw AuthorizationFailure(
        s"${actor.displayName} is not allowed to perform ${Permission.SubmitTournamentLineup} for club ${club.id.value}"
      )

  private final case class AcceptClubTournamentCommand(
      clubId: ClubId,
      tournamentId: TournamentId,
      actor: AccessPrincipal
  )
