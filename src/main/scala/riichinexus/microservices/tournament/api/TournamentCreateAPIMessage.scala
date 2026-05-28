package riichinexus.microservices.tournament.api

import java.time.Instant
import java.util.NoSuchElementException

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.domain.model.*
import riichinexus.microservices.auth.domain.model.*
import riichinexus.microservices.tournament.domain.model.*
import riichinexus.microservices.player.objects.*
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.player.tables.player.PlayerTable
import riichinexus.microservices.tournament.domain.TournamentRuntimeDefaults
import riichinexus.microservices.tournament.objects.apiTypes.*
import riichinexus.microservices.tournament.objects.apiTypes.AssignTournamentAdminRequest.given
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
          createTournament(context.connection, input)
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
      input: CreateTournamentInput
  ): Tournament =
    validateRequest(input)
    val normalizedStages = resolveNormalizedStages(input.stages)
    val adminPlayer = resolveAdminPlayer(connection, input.admin)
    ensureTournamentDoesNotExist(connection, input)
    val baseTournament = buildTournament(input, normalizedStages)
    val tournament = assignAdmin(baseTournament, input.admin)
    grantAdminRole(connection, tournament, input, adminPlayer)
    saveTournament(connection, tournament)

  private def validateRequest(input: CreateTournamentInput): Unit =
    require(input.name.trim.nonEmpty, "Tournament name cannot be empty")
    require(input.organizer.trim.nonEmpty, "Tournament organizer cannot be empty")
    require(input.startsAt.isBefore(input.endsAt), "Tournament start time must be earlier than end time")

  private def resolveNormalizedStages(stages: Vector[TournamentStage]): Vector[TournamentStage] =
    val normalizedStages = TournamentDefaults.initialStages(stages)
      .map(TournamentRuntimeDefaults.normalizeStage)
      .sortBy(_.order)
    requireUniqueStageConfiguration(normalizedStages)
    normalizedStages

  private def resolveAdminPlayer(
      connection: java.sql.Connection,
      admin: Option[PlayerId]
  ): Option[Player] =
    admin.map { targetAdminId =>
      val player = PlayerTable
        .findById(connection, targetAdminId)
        .getOrElse(throw NoSuchElementException(s"Player ${targetAdminId.value} was not found"))
      requireActivePlayer(player, s"Player ${targetAdminId.value} cannot administer tournaments")
      player
    }

  private def ensureTournamentDoesNotExist(
      connection: java.sql.Connection,
      input: CreateTournamentInput
  ): Unit =
    riichinexus.microservices.tournament.tables.tournament.TournamentTable
      .findByNameAndOrganizer(connection, input.name, input.organizer)
      .foreach { existing =>
        throw IllegalArgumentException(
          s"Tournament ${existing.id.value} already exists for ${input.name} by ${input.organizer}"
        )
      }

  private def buildTournament(
      input: CreateTournamentInput,
      normalizedStages: Vector[TournamentStage]
  ): Tournament =
    Tournament(
      id = IdGenerator.tournamentId(),
      name = input.name,
      organizer = input.organizer,
      startsAt = input.startsAt,
      endsAt = input.endsAt,
      admins = Vector.empty,
      stages = normalizedStages
    )

  private def assignAdmin(tournament: Tournament, admin: Option[PlayerId]): Tournament =
    admin.fold(tournament)(tournament.assignAdmin)

  private def grantAdminRole(
      connection: java.sql.Connection,
      tournament: Tournament,
      input: CreateTournamentInput,
      adminPlayer: Option[Player]
  ): Unit =
    adminPlayer.foreach { player =>
      PlayerTable.save(
        connection,
        player.grantRole(
          RoleGrant.tournamentAdmin(tournament.id, input.startsAt, AccessPrincipal.system.playerId)
        )
      )
    }

  private def saveTournament(
      connection: java.sql.Connection,
      tournament: Tournament
  ): Tournament =
    riichinexus.microservices.tournament.tables.tournament.TournamentTable.save(connection, tournament)

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
