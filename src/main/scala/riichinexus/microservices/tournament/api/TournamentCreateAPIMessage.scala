package riichinexus.microservices.tournament.api

import java.time.Instant
import java.util.NoSuchElementException

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.bootstrap.TournamentModuleContext
import riichinexus.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.dictionary.domain.RuntimeDictionary
import riichinexus.microservices.tournament.objects.*
import riichinexus.microservices.tournament.objects.apiTypes.{Table as _, TableSeat as _, StageStandingEntry as _, StageRankingSnapshot as _, StageAdvancementSnapshot as _, KnockoutBracketSlot as _, KnockoutBracketResult as _, KnockoutBracketMatch as _, KnockoutBracketRound as _, KnockoutBracketSnapshot as _, *}
import riichinexus.microservices.tournament.objects.apiTypes.ManagementRequests.given
import riichinexus.microservices.tournament.objects.apiTypes.SettlementRequests.given
import riichinexus.microservices.tournament.objects.apiTypes.StageRequests.given
import riichinexus.microservices.tournament.objects.apiTypes.TableRequests.given
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
          createTournament(module, input)
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
      module: TournamentModuleContext,
      input: CreateTournamentInput
  ): Tournament =
    validateRequest(input)
    val normalizedStages = resolveNormalizedStages(module, input.stages)
    validateAdmin(module, input.admin)
    val tournament = resolveTournament(module, input, normalizedStages)
    grantAdminRole(module, tournament, input)
    module.tournamentRepository.save(input.admin.fold(tournament)(tournament.assignAdmin))

  private def validateRequest(input: CreateTournamentInput): Unit =
    require(input.name.trim.nonEmpty, "Tournament name cannot be empty")
    require(input.organizer.trim.nonEmpty, "Tournament organizer cannot be empty")
    require(input.startsAt.isBefore(input.endsAt), "Tournament start time must be earlier than end time")

  private def resolveNormalizedStages(
      module: TournamentModuleContext,
      stages: Vector[TournamentStage]
  ): Vector[TournamentStage] =
    val dictionarySnapshot = RuntimeDictionary.snapshot(module.globalDictionaryRepository)
    val normalizedStages = TournamentDefaults.initialStages(stages)
      .map(stage => normalizeStage(stage, dictionarySnapshot))
      .sortBy(_.order)
    requireUniqueStageConfiguration(normalizedStages)
    normalizedStages

  private def validateAdmin(module: TournamentModuleContext, admin: Option[PlayerId]): Unit =
    admin.foreach { targetAdminId =>
      val adminPlayer = module.playerRepository
        .findById(targetAdminId)
        .getOrElse(throw NoSuchElementException(s"Player ${targetAdminId.value} was not found"))
      requireActivePlayer(adminPlayer, s"Player ${targetAdminId.value} cannot administer tournaments")
    }

  private def resolveTournament(
      module: TournamentModuleContext,
      input: CreateTournamentInput,
      normalizedStages: Vector[TournamentStage]
  ): Tournament =
    module.tournamentRepository.findByNameAndOrganizer(input.name, input.organizer) match
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
      module: TournamentModuleContext,
      tournament: Tournament,
      input: CreateTournamentInput
  ): Unit =
    input.admin.foreach { targetAdminId =>
      module.playerRepository.findById(targetAdminId).foreach { adminPlayer =>
        module.playerRepository.save(
          adminPlayer.grantRole(
            RoleGrant.tournamentAdmin(tournament.id, input.startsAt, AccessPrincipal.system.playerId)
          )
        )
      }
    }

  private def normalizeStage(
      stage: TournamentStage,
      dictionarySnapshot: RuntimeDictionary.DictionarySnapshot
  ): TournamentStage =
    val templatedStage =
      RuntimeDictionary.resolveStageRules(stage, dictionarySnapshot)

    if templatedStage.advancementRule.ruleType == AdvancementRuleType.Custom &&
        templatedStage.advancementRule.note.contains("unconfigured") &&
        templatedStage.advancementRule.templateKey.isEmpty
    then templatedStage.copy(advancementRule = AdvancementRule.defaultFor(templatedStage.format))
    else templatedStage

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
