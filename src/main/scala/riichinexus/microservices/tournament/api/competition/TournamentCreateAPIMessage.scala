package riichinexus.microservices.tournament.api.competition
import riichinexus.microservices.tournament.domain.competition.functions.TournamentViewFunctions
import riichinexus.microservices.player.api.`private`.RecordPlayerTournamentAdminGrantPrivateAPIMessage
import riichinexus.microservices.player.api.`private`.ResolvePlayerPrivateAPIMessage

import java.time.Instant
import java.util.NoSuchElementException

import cats.effect.IO
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.microservices.player.objects.PlayerId
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
import riichinexus.microservices.tournament.objects.stage.rules.knockout.{KnockoutRuleConfig, KnockoutSeedingPolicy}
import riichinexus.microservices.tournament.objects.stage.rules.swiss.{SwissPairingMethod, SwissRuleConfig}
import riichinexus.microservices.tournament.objects.stage.lifecycle.apiTypes.CreateTournamentStageRequest
import riichinexus.microservices.tournament.objects.competition.apiTypes.{CreateTournamentRequest}
import riichinexus.microservices.tournament.objects.competition.{TournamentSummaryView}
import riichinexus.microservices.tournament.tables.tournaments.TournamentTable

/** 创建新的赛事草稿。 */
final case class TournamentCreateAPIMessage(
    request: CreateTournamentRequest
) extends APIMessage[TournamentSummaryView]:

  override def plan(context: ApiPlanContext): IO[TournamentSummaryView] =
    for
      adminId <- IO.blocking(request.adminId.map(PlayerId(_)))
      requestedStages <- IO.blocking(request.stages.map(tournamentStage))
      _ <- IO.blocking(validateRequest(request.name, request.organizer, request.startsAt, request.endsAt))
      normalizedStages <- IO.blocking(resolveNormalizedStages(requestedStages))
      adminPlayer <- resolveAdminPlayer(context, adminId)
      tournament <- IO.blocking(buildTournamentWithAdmin(request.name, request.organizer, request.startsAt, request.endsAt, adminId, normalizedStages))
      _ <- ensureTournamentNameAvailable(context, request.name, request.organizer)
      _ <- grantAdminRole(context, tournament, request.startsAt, adminPlayer)
      savedTournament <- saveTournament(context, tournament)
    yield TournamentViewFunctions.tournamentSummaryView(savedTournament)

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
            ruleType = rule,
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
          pairingMethod = request.pairingMethod.getOrElse(SwissPairingMethod.BalancedElo),
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
          seedingPolicy = request.seedingPolicy.getOrElse(KnockoutSeedingPolicy.Rating),
          repechageEnabled = request.repechageEnabled.getOrElse(false)
        )
      )
    else None

  private def validateRequest(name: String, organizer: String, startsAt: Instant, endsAt: Instant): Unit =
    require(name.trim.nonEmpty, "Tournament name cannot be empty")
    require(organizer.trim.nonEmpty, "Tournament organizer cannot be empty")
    require(startsAt.isBefore(endsAt), "Tournament start time must be earlier than end time")

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
      name: String,
      organizer: String,
      startsAt: Instant,
      endsAt: Instant,
      admin: Option[PlayerId],
      normalizedStages: Vector[TournamentStage]
  ): Tournament =
    assignAdmin(buildTournament(name, organizer, startsAt, endsAt, normalizedStages), admin)

  private def ensureTournamentNameAvailable(
      context: ApiPlanContext,
      name: String,
      organizer: String
  ): IO[Unit] =
    IO.blocking(ensureTournamentDoesNotExist(context.connection, name, organizer))

  private def ensureTournamentDoesNotExist(
      connection: java.sql.Connection,
      name: String,
      organizer: String
  ): Unit =
    TournamentTable
      .findByNameAndOrganizer(connection, name, organizer)
      .foreach { existing =>
        throw IllegalArgumentException(
          s"Tournament ${existing.id.value} already exists for ${name} by ${organizer}"
        )
      }

  private def buildTournament(
      name: String,
      organizer: String,
      startsAt: Instant,
      endsAt: Instant,
      normalizedStages: Vector[TournamentStage]
  ): Tournament =
    Tournament(
      id = TournamentIdGenerator.tournamentId(),
      name = name,
      organizer = organizer,
      startsAt = startsAt,
      endsAt = endsAt,
      admins = Vector.empty,
      stages = normalizedStages
    )

  private def assignAdmin(tournament: Tournament, admin: Option[PlayerId]): Tournament =
    admin.fold(tournament)(playerId => TournamentFunctions.assignAdmin(tournament, playerId))

  private def grantAdminRole(
      context: ApiPlanContext,
      tournament: Tournament,
      startsAt: Instant,
      adminPlayer: Option[PlayerPrivateView]
  ): IO[Unit] =
    adminPlayer match
      case Some(player) =>
        RecordPlayerTournamentAdminGrantPrivateAPIMessage(
          player.id,
          tournament.id,
          startsAt,
          None
        ).plan(context).map(_ => ())
      case None => IO.unit

  private def saveTournament(
      context: ApiPlanContext,
      tournament: Tournament
  ): IO[Tournament] =
    IO.blocking {
      TournamentTable.save(context.connection, tournament)
    }

  private def requireUniqueStageConfiguration(stages: Vector[TournamentStage]): Unit =
    if stages.map(_.id).distinct.size != stages.size then
      throw IllegalArgumentException("Tournament stages must have unique ids")
    if stages.map(_.order).distinct.size != stages.size then
      throw IllegalArgumentException("Tournament stages must have unique ordering")

  private def requireActivePlayer(player: PlayerPrivateView, context: String): Unit =
    if player.status != PlayerStatus.Active then
      throw IllegalArgumentException(context)
