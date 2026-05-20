package riichinexus.microservices.tournament.api

import java.time.Instant
import java.util.NoSuchElementException

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.dictionary.domain.RuntimeDictionary
import riichinexus.microservices.tournament.objects.*
import riichinexus.microservices.tournament.objects.apiTypes.*
import riichinexus.microservices.tournament.objects.apiTypes.ManagementRequests.given
import riichinexus.microservices.tournament.objects.apiTypes.SettlementRequests.given
import riichinexus.microservices.tournament.objects.apiTypes.StageRequests.given
import riichinexus.microservices.tournament.objects.apiTypes.TableRequests.given
import upickle.default.*

final case class TournamentCreateAPIMessage(
    name: String,
    organizer: String,
    startsAt: Instant,
    endsAt: Instant,
    stages: Vector[CreateTournamentStageRequest],
    adminId: Option[String] = None
) extends APIMessage[TournamentSummaryView] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[TournamentSummaryView] =
    IO {
      val module = context.support.tournamentModule
      val request = CreateTournamentRequest(name, organizer, startsAt, endsAt, stages, adminId)

      module.transactionManager.inTransaction {
        require(request.name.trim.nonEmpty, "Tournament name cannot be empty")
        require(request.organizer.trim.nonEmpty, "Tournament organizer cannot be empty")
        require(request.startsAt.isBefore(request.endsAt), "Tournament start time must be earlier than end time")

        val dictionarySnapshot = RuntimeDictionary.snapshot(module.globalDictionaryRepository)
        val normalizedStages = TournamentDefaults.initialStages(request.toStages)
          .map(stage => normalizeStage(stage, dictionarySnapshot))
          .sortBy(_.order)
        requireUniqueStageConfiguration(normalizedStages)

        request.admin.foreach { targetAdminId =>
          val adminPlayer = module.playerRepository
            .findById(targetAdminId)
            .getOrElse(throw NoSuchElementException(s"Player ${targetAdminId.value} was not found"))
          requireActivePlayer(adminPlayer, s"Player ${targetAdminId.value} cannot administer tournaments")
        }

        val tournament = module.tournamentRepository.findByNameAndOrganizer(request.name, request.organizer) match
          case Some(existing) =>
            existing.copy(
              startsAt = request.startsAt,
              endsAt = request.endsAt,
              stages = normalizedStages
            )
          case None =>
            Tournament(
              id = IdGenerator.tournamentId(),
              name = request.name,
              organizer = request.organizer,
              startsAt = request.startsAt,
              endsAt = request.endsAt,
              admins = request.admin.toVector,
              stages = normalizedStages
            )

        request.admin.foreach { targetAdminId =>
          module.playerRepository.findById(targetAdminId).foreach { adminPlayer =>
            module.playerRepository.save(
              adminPlayer.grantRole(
                RoleGrant.tournamentAdmin(tournament.id, request.startsAt, AccessPrincipal.system.playerId)
              )
            )
          }
        }

        TournamentSummaryView.fromDomain(
          module.tournamentRepository.save(
            request.admin.fold(tournament)(tournament.assignAdmin)
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
