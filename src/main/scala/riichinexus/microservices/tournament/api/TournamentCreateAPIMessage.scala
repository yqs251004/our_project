package riichinexus.microservices.tournament.api

import java.time.Instant
import java.util.NoSuchElementException

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.bootstrap.TournamentModuleContext
import riichinexus.domain.model.*
import riichinexus.microservices.player.objects.*
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.player.tables.player.PlayerTable
import riichinexus.microservices.tournament.domain.TournamentRuntimeDefaults
import riichinexus.microservices.tournament.objects.apiTypes.*
import riichinexus.microservices.tournament.objects.apiTypes.*
import riichinexus.microservices.tournament.objects.apiTypes.ManagementRequests.given
import upickle.default.*

final case class TournamentCreateAPIMessage(
    request: CreateTournamentRequest
) extends APIMessage[TournamentSummaryView] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[TournamentSummaryView] =
    for
      input <- IO(resolveInput)
      module = context.support.tournamentModule
      tournament <- IO {
        module.transactionManager.inTransaction {
          createTournament(context.connection, module, input)
        }
      }
    yield TournamentSummaryView.fromDomain(tournament)

  private def resolveInput: CreateTournamentInput =
    CreateTournamentInput(
      name = request.name,
      organizer = request.organizer,
      startsAt = request.startsAt,
      endsAt = request.endsAt,
      admin = request.admin,
      stages = request.toStages
    )

  private def createTournament(
      connection: java.sql.Connection,
      module: TournamentModuleContext,
      input: CreateTournamentInput
  ): Tournament =
    validateRequest(input)
    val normalizedStages = resolveNormalizedStages(module, input.stages)
    validateAdmin(connection, input.admin)
    val tournament = resolveTournament(connection, input, normalizedStages)
    grantAdminRole(connection, tournament, input)
    riichinexus.microservices.tournament.tables.tournament.TournamentTable.save(connection, input.admin.fold(tournament)(tournament.assignAdmin))

  private def validateRequest(input: CreateTournamentInput): Unit =
    require(input.name.trim.nonEmpty, "Tournament name cannot be empty")
    require(input.organizer.trim.nonEmpty, "Tournament organizer cannot be empty")
    require(input.startsAt.isBefore(input.endsAt), "Tournament start time must be earlier than end time")

  private def resolveNormalizedStages(
      module: TournamentModuleContext,
      stages: Vector[TournamentStage]
  ): Vector[TournamentStage] =
    val normalizedStages = TournamentDefaults.initialStages(stages)
      .map(TournamentRuntimeDefaults.normalizeStage)
      .sortBy(_.order)
    requireUniqueStageConfiguration(normalizedStages)
    normalizedStages

  private def validateAdmin(connection: java.sql.Connection, admin: Option[PlayerId]): Unit =
    admin.foreach { targetAdminId =>
      val adminPlayer = PlayerTable
        .findById(connection, targetAdminId)
        .getOrElse(throw NoSuchElementException(s"Player ${targetAdminId.value} was not found"))
      requireActivePlayer(adminPlayer, s"Player ${targetAdminId.value} cannot administer tournaments")
    }

  private def resolveTournament(
      connection: java.sql.Connection,
      input: CreateTournamentInput,
      normalizedStages: Vector[TournamentStage]
  ): Tournament =
    riichinexus.microservices.tournament.tables.tournament.TournamentTable.findByNameAndOrganizer(connection, input.name, input.organizer) match
      case Some(existing) =>
        existing.copy(
          startsAt = input.startsAt,
          endsAt = input.endsAt,
          stages = normalizedStages
        )
      case None =>
        Tournament(
          id = IdGenerator.tournamentId(),
          name = input.name,
          organizer = input.organizer,
          startsAt = input.startsAt,
          endsAt = input.endsAt,
          admins = input.admin.toVector,
          stages = normalizedStages
        )

  private def grantAdminRole(
      connection: java.sql.Connection,
      tournament: Tournament,
      input: CreateTournamentInput
  ): Unit =
    input.admin.foreach { targetAdminId =>
      PlayerTable.findById(connection, targetAdminId).foreach { adminPlayer =>
        PlayerTable.save(
          connection,
          adminPlayer.grantRole(
            RoleGrant.tournamentAdmin(tournament.id, input.startsAt, AccessPrincipal.system.playerId)
          )
        )
      }
    }

  private def requireUniqueStageConfiguration(stages: Vector[TournamentStage]): Unit =
    if stages.map(_.id).distinct.size != stages.size then
      throw IllegalArgumentException("Tournament stages must have unique ids")
    if stages.map(_.order).distinct.size != stages.size then
      throw IllegalArgumentException("Tournament stages must have unique ordering")

  private def requireActivePlayer(player: Player, context: String): Unit =
    if player.status != PlayerStatus.Active then
      throw IllegalArgumentException(context)

  private final case class CreateTournamentInput(
      name: String,
      organizer: String,
      startsAt: Instant,
      endsAt: Instant,
      admin: Option[PlayerId],
      stages: Vector[TournamentStage]
  )
