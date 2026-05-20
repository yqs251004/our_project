package riichinexus.microservices.tournament.api

import java.time.Instant
import java.util.NoSuchElementException

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.bootstrap.TournamentModuleContext
import riichinexus.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.tournament.domain.StageLineupSupport
import riichinexus.microservices.tournament.objects.*
import riichinexus.microservices.tournament.objects.apiTypes.*
import riichinexus.microservices.tournament.objects.apiTypes.ManagementRequests.given
import riichinexus.microservices.tournament.objects.apiTypes.SettlementRequests.given
import riichinexus.microservices.tournament.objects.apiTypes.StageRequests.given
import riichinexus.microservices.tournament.objects.apiTypes.TableRequests.given
import upickle.default.*

final case class TournamentStageCompleteAPIMessage(tournamentId: String, stageId: String, request: CompleteStageRequest) extends APIMessage[StageAdvancementSnapshot] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[StageAdvancementSnapshot] =
    IO {
      val module = context.support.tournamentModule
      val tournamentIdValue = TournamentId(tournamentId)
      val stageIdValue = TournamentStageId(stageId)
      val actor = request.operator.map(context.support.principal).getOrElse(AccessPrincipal.system)
      val completedAt = Instant.now()

      module.transactionManager.inTransaction {
        module.tournamentRepository.findById(tournamentIdValue).map { tournament =>
          module.authorizationService.requirePermission(
            actor,
            Permission.ManageTournamentStages,
            tournamentId = Some(tournamentIdValue)
          )

          val stage = tournament.stages
            .find(_.id == stageIdValue)
            .getOrElse(throw NoSuchElementException(s"Stage ${stageIdValue.value} was not found"))
          val stageTables = module.tableRepository.findByTournamentAndStage(tournamentIdValue, stageIdValue)
          val isKnockoutStage =
            stage.format == StageFormat.Knockout ||
              stage.format == StageFormat.Finals ||
              stage.advancementRule.ruleType == AdvancementRuleType.KnockoutElimination

          if stageTables.size != stage.scheduledTableIds.size then
            throw IllegalArgumentException(
              s"Stage ${stageIdValue.value} cannot complete before every scheduled table is materialized"
            )

          if stageTables.exists(_.status != TableStatus.Archived) then
            throw IllegalArgumentException(
              s"Stage ${stageIdValue.value} cannot complete while tables are still active or under appeal"
            )

          if !isKnockoutStage then
            val participants = resolveParticipants(module, tournament, stage)
            val records = module.matchRecordRepository.findByTournamentAndStage(tournamentIdValue, stageIdValue)
            val effectiveRoundLimit = StageLineupSupport.effectiveRoundLimit(stage)
            val requiredTablesPerRound =
              expectedTablesPerRound(
                module = module,
                tournament = tournament,
                stage = stage,
                participants = participants,
                records = records,
                at = completedAt
              )
            val roundCounts = stageTables.groupBy(_.stageRoundNumber).view.mapValues(_.size).toMap
            val missingRounds = (1 to effectiveRoundLimit).filter(roundNumber =>
              roundCounts.getOrElse(roundNumber, 0) != requiredTablesPerRound
            )

            if stage.pendingTablePlans.nonEmpty || stage.currentRound < effectiveRoundLimit || missingRounds.nonEmpty then
              throw IllegalArgumentException(
                s"Stage ${stageIdValue.value} cannot complete before all $effectiveRoundLimit rounds are fully scheduled and archived"
              )

          val advancement =
            module.stageQueries.stageAdvancementPreview(tournamentIdValue, stageIdValue, completedAt)

          module.tournamentRepository.save(tournament.updateStage(stageIdValue, _.complete))
          advancement
        }
      }.getOrElse(throw NoSuchElementException("Resource not found"))
    }

  private def resolveParticipants(
      module: TournamentModuleContext,
      tournament: Tournament,
      stage: TournamentStage
  ): Vector[Player] =
    val clubsById = module.clubRepository.findByIds(
      (tournament.participatingClubs ++ tournament.whitelist.flatMap(_.clubId)).distinct
    ).map(club => club.id -> club).toMap

    val fallbackPlayerIds =
      val registeredClubMembers = tournament.participatingClubs.flatMap { clubId =>
        clubsById.get(clubId).toVector.flatMap(_.members)
      }
      val whitelistedPlayers = tournament.whitelist.flatMap(_.playerId)
      val whitelistedClubMembers = tournament.whitelist.flatMap { entry =>
        entry.clubId.toVector.flatMap(clubId => clubsById.get(clubId).toVector.flatMap(_.members))
      }

      (tournament.participatingPlayers ++ whitelistedPlayers ++ registeredClubMembers ++ whitelistedClubMembers).distinct

    val playersById = module.playerRepository.findByIds(
      (stage.lineupSubmissions.flatMap(_.seats.map(_.playerId)) ++ fallbackPlayerIds).distinct
    ).map(player => player.id -> player).toMap
    val stagePlayerIds = StageLineupSupport.resolveEligiblePlayers(stage, playersById.get)

    val targetPlayerIds =
      if stagePlayerIds.nonEmpty then stagePlayerIds else fallbackPlayerIds

    targetPlayerIds.flatMap { playerId =>
      playersById.get(playerId).filter(_.status == PlayerStatus.Active)
    }

  private def expectedTablesPerRound(
      module: TournamentModuleContext,
      tournament: Tournament,
      stage: TournamentStage,
      participants: Vector[Player],
      records: Vector[MatchRecord],
      at: Instant
  ): Int =
    stage.format match
      case StageFormat.Custom =>
        val selectedPlayers = selectCustomStageParticipants(
          module = module,
          tournament = tournament,
          stage = stage,
          participants = participants,
          history = records,
          roundNumber = math.max(1, stage.currentRound),
          at = at
        )
        selectedPlayers.size / 4
      case _ =>
        participants.size / 4

  private def selectCustomStageParticipants(
      module: TournamentModuleContext,
      tournament: Tournament,
      stage: TournamentStage,
      participants: Vector[Player],
      history: Vector[MatchRecord],
      roundNumber: Int,
      at: Instant
  ): Vector[Player] =
    val maxTables = math.max(1, math.min(participants.size / 4, customStageTableCount(stage, participants.size)))
    val targetParticipants = maxTables * 4
    val rankingOrder =
      if history.nonEmpty then
        val ranking = module.tournamentRuleEngine.buildStageRanking(
          tournament,
          stage,
          participants.map(_.id),
          history,
          at
        )
        ranking.entries.flatMap(entry => participants.find(_.id == entry.playerId))
      else Vector.empty

    val seededOrder =
      if rankingOrder.nonEmpty then rankingOrder
      else
        participants.sortBy(player => (-player.elo, player.nickname, player.id.value))

    val rotatedOrder =
      if seededOrder.isEmpty then seededOrder
      else rotateVector(seededOrder, (roundNumber - 1) % seededOrder.size)

    rotatedOrder.take(targetParticipants)

  private def customStageTableCount(
      stage: TournamentStage,
      participantCount: Int
  ): Int =
    val availableTables = participantCount / 4
    require(availableTables >= 1, s"Stage ${stage.id.value} needs at least one full table")
    stage.advancementRule.targetTableCount match
      case Some(value) =>
        require(value >= 1, s"Stage ${stage.id.value} targetTableCount must be positive")
        require(value <= availableTables, s"Stage ${stage.id.value} targetTableCount exceeds available tables")
        value
      case None =>
        availableTables

  private def rotateVector[A](values: Vector[A], shift: Int): Vector[A] =
    if values.isEmpty then values
    else
      val normalized = math.floorMod(shift, values.size)
      values.drop(normalized) ++ values.take(normalized)
