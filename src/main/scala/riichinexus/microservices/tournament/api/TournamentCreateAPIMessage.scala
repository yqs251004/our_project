package riichinexus.microservices.tournament.api
import riichinexus.microservices.player.api.`private`.{RecordPlayerTournamentAdminGrantPrivateAPIMessage, ResolvePlayerPrivateAPIMessage}

import java.time.Instant
import java.util.NoSuchElementException

import cats.effect.IO
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.microservices.tournament.domain.identity.functions.TournamentIdGenerator
import riichinexus.microservices.tournament.domain.stage.functions.rules.progression.AdvancementRuleFunctions
import riichinexus.microservices.tournament.domain.competition.functions.{TournamentDefaultsFunctions, TournamentFunctions}
import riichinexus.microservices.tournament.domain.stage.model.TournamentStage
import riichinexus.microservices.tournament.domain.competition.model.Tournament
import riichinexus.microservices.player.objects.`private`.PlayerPrivateView
import riichinexus.microservices.player.objects.PlayerStatus

import riichinexus.microservices.tournament.domain.competition.functions.TournamentRuntimeDefaults
import riichinexus.microservices.tournament.mahjongcore.objects.gamestate.MahjongRuleset
import riichinexus.microservices.tournament.objects.stage.rules.progression.{AdvancementRule, AdvancementRuleType}
import riichinexus.microservices.tournament.objects.stage.rules.knockout.KnockoutRuleConfig
import riichinexus.microservices.tournament.objects.stage.rules.swiss.SwissRuleConfig
import riichinexus.microservices.tournament.objects.stage.apiTypes.CreateTournamentStageRequest
import riichinexus.microservices.tournament.objects.competition.apiTypes.{CreateTournamentRequest, TournamentSummaryView}

import upickle.default.ReadWriter

/** 创建新的赛事草稿。 */
final case class TournamentCreateAPIMessage(
    request: CreateTournamentRequest
) extends APIMessage[TournamentSummaryView] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[TournamentSummaryView] =
    for
      input <- IO.blocking(resolveInput)
      _ <- IO.blocking(validateRequest(input))
      normalizedStages <- IO.blocking(resolveNormalizedStages(input.stages))
      adminPlayer <- resolveAdminPlayer(context, input.admin)
      tournament <- IO.blocking(buildTournamentWithAdmin(input, normalizedStages))
      _ <- ensureTournamentNameAvailable(context, input)
      _ <- grantAdminRole(context, tournament, input, adminPlayer)
      savedTournament <- saveTournament(context, tournament)
    yield TournamentSummaryView.fromDomain(savedTournament)

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
      id = TournamentIdGenerator.stageId(),
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
      mahjongRuleset = request.mahjongRuleset.getOrElse(MahjongRuleset()),
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
      context: ApiPlanContext,
      admin: Option[PlayerId]
  ): IO[Option[PlayerPrivateView]] =
    admin match
      case Some(targetAdminId) =>
        for
          player <- ResolvePlayerPrivateAPIMessage(targetAdminId)
            .plan(context)
            .map(_.getOrElse(throw NoSuchElementException(s"PlayerPrivateView ${targetAdminId.value} was not found")))
          _ <- IO.blocking(requireActivePlayer(player, s"PlayerPrivateView ${targetAdminId.value} cannot administer tournaments"))
        yield Some(player)
      case None => IO.pure(None)

  private def buildTournamentWithAdmin(
      input: CreateTournamentInput,
      normalizedStages: Vector[TournamentStage]
  ): Tournament =
    assignAdmin(buildTournament(input, normalizedStages), input.admin)

  private def ensureTournamentNameAvailable(
      context: ApiPlanContext,
      input: CreateTournamentInput
  ): IO[Unit] =
    IO.blocking(ensureTournamentDoesNotExist(context.connection, input))

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
      id = TournamentIdGenerator.tournamentId(),
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
      context: ApiPlanContext,
      tournament: Tournament,
      input: CreateTournamentInput,
      adminPlayer: Option[PlayerPrivateView]
  ): IO[Unit] =
    adminPlayer match
      case Some(player) =>
        RecordPlayerTournamentAdminGrantPrivateAPIMessage(
          player.id,
          tournament.id,
          input.startsAt,
          None
        ).plan(context).map(_ => ())
      case None => IO.unit

  private def saveTournament(
      context: ApiPlanContext,
      tournament: Tournament
  ): IO[Tournament] =
    IO.blocking {
      riichinexus.microservices.tournament.tables.tournaments.TournamentTable.save(context.connection, tournament)
    }

  private def requireUniqueStageConfiguration(stages: Vector[TournamentStage]): Unit =
    if stages.map(_.id).distinct.size != stages.size then
      throw IllegalArgumentException("Tournament stages must have unique ids")
    if stages.map(_.order).distinct.size != stages.size then
      throw IllegalArgumentException("Tournament stages must have unique ordering")

  private def requireActivePlayer(player: PlayerPrivateView, context: String): Unit =
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
