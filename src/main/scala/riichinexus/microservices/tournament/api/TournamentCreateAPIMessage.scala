package riichinexus.microservices.tournament.api
import riichinexus.microservices.player.api.`private`.*

import riichinexus.microservices.auth.domain.authorization.{AccessPrincipalFunctions, AuthorizationPolicyFunctions, RoleGrantFunctions}

import java.time.Instant
import java.util.NoSuchElementException

import cats.effect.IO
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.microservices.player.domain.functions.PlayerIdGenerator
import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.microservices.club.domain.functions.ClubIdGenerator
import riichinexus.microservices.club.objects.clubmanagement.ClubId
import riichinexus.microservices.club.objects.membershipmanagement.MembershipApplicationId
import riichinexus.microservices.tournament.domain.functions.TournamentIdGenerator
import riichinexus.microservices.tournament.objects.lineupmanagement.LineupSubmissionId
import riichinexus.microservices.tournament.objects.paifumanagement.PaifuId
import riichinexus.microservices.tournament.objects.recordmanagement.MatchRecordId
import riichinexus.microservices.tournament.objects.settlementmanagement.SettlementSnapshotId
import riichinexus.microservices.tournament.objects.tablemanagement.TableId
import riichinexus.microservices.tournament.objects.tournamentmanagement.{TournamentId, TournamentStageId}
import riichinexus.microservices.tournament.appeal.domain.functions.AppealIdGenerator
import riichinexus.microservices.tournament.appeal.objects.ticketmanagement.AppealTicketId
import riichinexus.microservices.auth.domain.functions.AuthIdGenerator
import riichinexus.microservices.auth.objects.sessionmanagement.GuestSessionId
import riichinexus.microservices.audit.domain.functions.AuditIdGenerator
import riichinexus.microservices.audit.domain.auditevent.AuditEventId
import riichinexus.microservices.opsanalytics.domain.functions.OpsAnalyticsIdGenerator
import riichinexus.microservices.opsanalytics.objects.advancedstats.AdvancedStatsRecomputeTaskId
import riichinexus.microservices.auth.domain.model.*
import riichinexus.microservices.tournament.domain.rulesmanagement.functions.stageprogression.AdvancementRuleFunctions
import riichinexus.microservices.tournament.domain.tournamentmanagement.functions.{TournamentDefaultsFunctions, TournamentFunctions}
import riichinexus.microservices.tournament.domain.lineupmanagement.model.*
import riichinexus.microservices.tournament.domain.recordmanagement.model.*
import riichinexus.microservices.tournament.domain.settlementmanagement.model.*
import riichinexus.microservices.tournament.domain.tablemanagement.model.*
import riichinexus.microservices.tournament.domain.tournamentmanagement.model.*
import riichinexus.microservices.player.domain.Player
import riichinexus.microservices.player.objects.*
import riichinexus.system.json.JsonCodecs.given
import riichinexus.microservices.player.api.{CreatePlayerAPIMessage, GetPlayerAPIMessage, ListPlayersAPIMessage}
import riichinexus.microservices.tournament.domain.tournamentmanagement.functions.TournamentRuntimeDefaults
import riichinexus.microservices.tournament.mahjongcore.objects.gamestate.MahjongRuleset
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
      tournament <- createTournament(context, input)
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
      id = request.id.map(TournamentStageId(_)).getOrElse(TournamentIdGenerator.stageId()),
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

  private def createTournament(
      context: ApiPlanContext,
      input: CreateTournamentInput
  ): IO[Tournament] =
    for
      _ <- IO.blocking(validateRequest(input))
      normalizedStages <- IO.blocking(resolveNormalizedStages(input.stages))
      adminPlayer <- resolveAdminPlayer(context, input.admin)
      tournament <- IO.blocking {
        ensureTournamentDoesNotExist(context.connection, input)
        val baseTournament = buildTournament(input, normalizedStages)
        assignAdmin(baseTournament, input.admin)
      }
      _ <- grantAdminRole(context, tournament, input, adminPlayer)
      savedTournament <- IO.blocking(saveTournament(context.connection, tournament))
    yield savedTournament

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
  ): IO[Option[Player]] =
    admin match
      case Some(targetAdminId) =>
        ResolvePlayerPrivateAPIMessage(targetAdminId)
          .plan(context)
          .map(_.getOrElse(throw NoSuchElementException(s"Player ${targetAdminId.value} was not found")))
          .flatMap { player =>
            IO.blocking(requireActivePlayer(player, s"Player ${targetAdminId.value} cannot administer tournaments"))
              .as(Some(player))
          }
      case None => IO.pure(None)

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
      adminPlayer: Option[Player]
  ): IO[Unit] =
    adminPlayer match
      case Some(player) =>
        SavePlayerPrivateAPIMessage(
          grantRole(
            player,
            RoleGrantFunctions.tournamentAdmin(tournament.id, input.startsAt, AccessPrincipalFunctions.system.playerId)
          )
        ).plan(context).map(_ => ())
      case None => IO.unit

  private def grantRole(player: Player, grant: RoleGrant): Player =
    val normalized = player.roleGrants.filterNot(existing =>
      existing.role == grant.role &&
        existing.clubId == grant.clubId &&
        existing.tournamentId == grant.tournamentId
    )
    player.copy(roleGrants = (normalized :+ grant).sortBy(_.grantedAt.toEpochMilli))

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
