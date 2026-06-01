package riichinexus.microservices.tournament.api

import riichinexus.microservices.auth.domain.functions.{AccessPrincipalFunctions, AuthorizationPolicyFunctions, RoleGrantFunctions}

import java.time.Instant
import java.util.NoSuchElementException

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.domain.model.*
import riichinexus.microservices.auth.domain.model.*
import riichinexus.microservices.tournament.domain.rulesmanagement.functions.stageprogression.AdvancementRuleFunctions
import riichinexus.microservices.tournament.domain.tournamentmanagement.functions.{TournamentDefaultsFunctions, TournamentFunctions}
import riichinexus.microservices.tournament.domain.lineupmanagement.model.*
import riichinexus.microservices.tournament.domain.recordmanagement.model.*
import riichinexus.microservices.tournament.domain.settlementmanagement.model.*
import riichinexus.microservices.tournament.domain.tablemanagement.model.*
import riichinexus.microservices.tournament.domain.tournamentmanagement.model.*
import riichinexus.microservices.player.domain.functions.PlayerRoleFunctions
import riichinexus.microservices.player.domain.Player
import riichinexus.microservices.player.objects.*
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.player.api.{CreatePlayerAPIMessage, GetPlayerAPIMessage, ListPlayersAPIMessage}
import riichinexus.microservices.tournament.domain.tournamentmanagement.functions.TournamentRuntimeDefaults
import riichinexus.microservices.tournament.objects.rulesmanagement.stageprogression.{AdvancementRule, AdvancementRuleType}
import riichinexus.microservices.tournament.objects.rulesmanagement.knockout.KnockoutRuleConfig
import riichinexus.microservices.tournament.objects.rulesmanagement.swiss.SwissRuleConfig
import riichinexus.microservices.tournament.objects.lineupmanagement.apiTypes.*
import riichinexus.microservices.tournament.objects.paifumanagement.apiTypes.*
import riichinexus.microservices.tournament.objects.recordmanagement.apiTypes.*
import riichinexus.microservices.tournament.objects.rulesmanagement.apiTypes.*
import riichinexus.microservices.tournament.objects.settlementmanagement.apiTypes.*
import riichinexus.microservices.tournament.objects.tablemanagement.apiTypes.*
import riichinexus.microservices.tournament.objects.tournamentmanagement.apiTypes.*
import riichinexus.microservices.tournament.objects.tournamentmanagement.apiTypes.AssignTournamentAdminRequest.given
import upickle.default.*

final case class TournamentCreateAPIMessage(
    request: CreateTournamentRequest
) extends APIMessage[TournamentSummaryView] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[TournamentSummaryView] =
    for
      input <- IO.blocking(resolveInput)
      module = context.support.tournamentModule
      tournament <- IO.blocking {
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
      admin = request.adminId.map(PlayerId(_)),
      stages = request.stages.map(tournamentStage)
    )

  private def tournamentStage(request: CreateTournamentStageRequest): TournamentStage =
    TournamentStage(
      id = request.id.map(TournamentStageId(_)).getOrElse(IdGenerator.stageId()),
      name = request.name,
      format = request.format,
      order = request.order,
      roundCount = request.roundCount,
      advancementRule = request.advancementRuleType
        .map(rule =>
          AdvancementRule(
            ruleType = AdvancementRuleType.valueOf(rule),
            cutSize = request.cutSize,
            thresholdScore = request.thresholdScore,
            targetTableCount = request.targetTableCount,
            templateKey = request.ruleTemplateKey,
            note = request.note
          )
        )
        .getOrElse(
          AdvancementRuleFunctions.defaultFor(request.format).copy(
            templateKey = request.ruleTemplateKey,
            note = request.note.orElse(AdvancementRuleFunctions.defaultFor(request.format).note)
          )
        ),
      swissRule = swissRule(request),
      knockoutRule = knockoutRule(request),
      schedulingPoolSize = request.schedulingPoolSize.getOrElse(4)
    )

  private def swissRule(request: CreateTournamentStageRequest): Option[SwissRuleConfig] =
    if request.pairingMethod.isDefined || request.carryOverPoints.isDefined || request.maxRounds.isDefined then
      Some(
        SwissRuleConfig(
          pairingMethod = request.pairingMethod.map(_.trim.toLowerCase).getOrElse("balanced-elo"),
          carryOverPoints = request.carryOverPoints.getOrElse(true),
          maxRounds = request.maxRounds
        )
      )
    else None

  private def knockoutRule(request: CreateTournamentStageRequest): Option[KnockoutRuleConfig] =
    if request.bracketSize.isDefined || request.thirdPlaceMatch.isDefined || request.seedingPolicy.isDefined || request.repechageEnabled.isDefined then
      Some(
        KnockoutRuleConfig(
          bracketSize = request.bracketSize,
          thirdPlaceMatch = request.thirdPlaceMatch.getOrElse(false),
          seedingPolicy = request.seedingPolicy.map(_.trim.toLowerCase).getOrElse("rating"),
          repechageEnabled = request.repechageEnabled.getOrElse(false)
        )
      )
    else None

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
    val normalizedStages = TournamentDefaultsFunctions.initialStages(stages)
      .map(TournamentRuntimeDefaults.normalizeStage)
      .sortBy(_.order)
    requireUniqueStageConfiguration(normalizedStages)
    normalizedStages

  private def resolveAdminPlayer(
      connection: java.sql.Connection,
      admin: Option[PlayerId]
  ): Option[Player] =
    admin.map { targetAdminId =>
      val player = GetPlayerAPIMessage.findPlayer(connection, targetAdminId)
        .getOrElse(throw NoSuchElementException(s"Player ${targetAdminId.value} was not found"))
      requireActivePlayer(player, s"Player ${targetAdminId.value} cannot administer tournaments")
      player
    }

  private def ensureTournamentDoesNotExist(
      connection: java.sql.Connection,
      input: CreateTournamentInput
  ): Unit =
    riichinexus.microservices.tournament.tables.tournaments.TournamentTable
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
    admin.fold(tournament)(playerId => TournamentFunctions.assignAdmin(tournament, playerId))

  private def grantAdminRole(
      connection: java.sql.Connection,
      tournament: Tournament,
      input: CreateTournamentInput,
      adminPlayer: Option[Player]
  ): Unit =
    adminPlayer.foreach { player =>
      CreatePlayerAPIMessage.persistPlayer(
        connection,
        PlayerRoleFunctions.grantRole(
          player,
          RoleGrantFunctions.tournamentAdmin(tournament.id, input.startsAt, AccessPrincipalFunctions.system.playerId)
        )
      )
    }

  private def saveTournament(
      connection: java.sql.Connection,
      tournament: Tournament
  ): Tournament =
    riichinexus.microservices.tournament.tables.tournaments.TournamentTable.save(connection, tournament)

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
