package riichinexus.microservices.tournament.api

import java.time.Instant
import java.util.NoSuchElementException

import riichinexus.application.ports.*
import riichinexus.domain.event.*
import riichinexus.domain.model.*
import riichinexus.domain.service.*
import riichinexus.microservices.dictionary.api.RuntimeDictionarySupport

private[tournament] trait TournamentStageWorkflow extends TournamentWorkflowSupport:
  def addStage(
      tournamentId: TournamentId,
      stage: TournamentStage,
      actor: AccessPrincipal
  ): Option[Tournament] =
    transactionManager.inTransaction {
      tournamentRepository.findById(tournamentId).map { tournament =>
        if tournament.status == TournamentStatus.Completed || tournament.status == TournamentStatus.Archived then
          throw IllegalArgumentException(
            s"Cannot add stages to tournament ${tournamentId.value} in status ${tournament.status}"
          )

        authorizationService.requirePermission(
          actor,
          Permission.ManageTournamentStages,
          tournamentId = Some(tournamentId)
        )

        val dictionarySnapshot = RuntimeDictionarySupport.snapshot(globalDictionaryRepository)
        tournamentRepository.save(tournament.addStage(normalizeStage(stage, dictionarySnapshot)))
      }
    }

  def configureStageRules(
      tournamentId: TournamentId,
      stageId: TournamentStageId,
      advancementRule: AdvancementRule,
      swissRule: Option[SwissRuleConfig],
      knockoutRule: Option[KnockoutRuleConfig],
      schedulingPoolSize: Option[Int],
      actor: AccessPrincipal
  ): Option[Tournament] =
    transactionManager.inTransaction {
      tournamentRepository.findById(tournamentId).map { tournament =>
        val currentStage = requireStage(tournament, stageId)
        authorizationService.requirePermission(
          actor,
          Permission.ConfigureTournamentRules,
          tournamentId = Some(tournamentId)
        )

        val dictionarySnapshot = RuntimeDictionarySupport.snapshot(globalDictionaryRepository)
        val configuredStage = normalizeStage(
          currentStage.withRules(
            advancementRule,
            swissRule,
            knockoutRule,
            schedulingPoolSize.getOrElse(currentStage.schedulingPoolSize)
          ),
          dictionarySnapshot
        )

        tournamentRepository.save(
          tournament.updateStage(stageId, _ => configuredStage)
        )
      }
    }

  def submitLineup(
      tournamentId: TournamentId,
      stageId: TournamentStageId,
      submission: StageLineupSubmission,
      actor: AccessPrincipal
  ): Option[Tournament] =
    transactionManager.inTransaction {
      tournamentRepository.findById(tournamentId).map { tournament =>
        val stage = requireStage(tournament, stageId)
        val submissionPlayerIds = submission.seats.map(_.playerId).distinct
        val conflictingPlayers = stage.lineupSubmissions
          .filterNot(_.clubId == submission.clubId)
          .flatMap(existing => existing.seats.map(_.playerId -> existing.clubId))
          .groupBy(_._1)
          .collect {
            case (playerId, assignments)
                if submissionPlayerIds.contains(playerId) &&
                  assignments.map(_._2).distinct.nonEmpty =>
              playerId.value
          }
          .toVector

        if conflictingPlayers.nonEmpty then
          throw IllegalArgumentException(
            s"Stage ${stageId.value} already has player(s) assigned by another club: ${conflictingPlayers.mkString(", ")}"
          )

        val club = clubRepository
          .findById(submission.clubId)
          .getOrElse(throw NoSuchElementException(s"Club ${submission.clubId.value} was not found"))
        ensureClubActive(club)
        requireClubLineupCapability(actor, club)

        if !actor.isSuperAdmin && actor.playerId.exists(_ != submission.submittedBy) then
          throw AuthorizationFailure("Lineup submitter must match the acting principal")

        val isClubRegistered =
          tournament.participatingClubs.contains(submission.clubId) ||
            tournament.whitelist.exists(_.clubId.contains(submission.clubId))

        if !isClubRegistered then
          throw IllegalArgumentException(
            s"Club ${submission.clubId.value} is not whitelisted for tournament ${tournamentId.value}"
          )

        submission.seats.foreach { seat =>
          val playerId = seat.playerId
          if !club.members.contains(playerId) then
            throw IllegalArgumentException(
              s"Player ${playerId.value} is not a member of club ${submission.clubId.value}"
            )

          val player = playerRepository
            .findById(playerId)
            .getOrElse(throw NoSuchElementException(s"Player ${playerId.value} was not found"))
          requireActivePlayer(player, s"Player ${playerId.value} cannot be submitted to tournament lineups")
        }

        tournamentRepository.save(
          tournament.updateStage(stageId, _.submitLineup(submission))
        )
      }
    }

  def scheduleStageTables(
      tournamentId: TournamentId,
      stageId: TournamentStageId,
      actor: AccessPrincipal = AccessPrincipal.system
  ): Vector[Table] =
    transactionManager.inTransaction {
      authorizationService.requirePermission(
        actor,
        Permission.ManageTournamentStages,
        tournamentId = Some(tournamentId)
      )

      val tournament = tournamentRepository
        .findById(tournamentId)
        .getOrElse(throw IllegalArgumentException(s"Tournament ${tournamentId.value} was not found"))

      val stage = tournament.stages
        .find(_.id == stageId)
        .getOrElse(throw IllegalArgumentException(s"Stage ${stageId.value} was not found"))

      if tournament.status == TournamentStatus.Draft then
        throw IllegalArgumentException(
          s"Tournament ${tournamentId.value} must be published before scheduling tables"
        )

      val isKnockoutStage =
        stage.format == StageFormat.Knockout ||
          stage.format == StageFormat.Finals ||
          stage.advancementRule.ruleType == AdvancementRuleType.KnockoutElimination

      if isKnockoutStage then
        val materialized = knockoutStageCoordinator.materializeUnlockedTables(tournamentId, stageId)
        if materialized.nonEmpty then
          tableRepository.findByTournamentAndStage(tournamentId, stageId).sortBy(table =>
            (table.stageRoundNumber, table.tableNo, table.id.value)
          )
        else
          tableRepository.findByTournamentAndStage(tournamentId, stageId).sortBy(table =>
            (table.stageRoundNumber, table.tableNo, table.id.value)
          )
      else
        val tournamentPlayers = resolveParticipants(tournament, stage)
        if tournamentPlayers.size < 4 then
          throw IllegalArgumentException(
            s"Stage ${stageId.value} needs at least four active players before scheduling"
          )
        if stage.format != StageFormat.Custom && tournamentPlayers.size % 4 != 0 then
          throw IllegalArgumentException(
            s"Stage ${stageId.value} requires player counts divisible by four; got ${tournamentPlayers.size}"
          )

        val existingTables = tableRepository.findByTournamentAndStage(tournamentId, stageId)
        val preparedTournament =
          prepareNonKnockoutRoundIfNeeded(
            tournament = tournament,
            stage = stage,
            participants = tournamentPlayers,
            existingTables = existingTables
          )
        val preparedStage = requireStage(preparedTournament, stageId)
        val refreshedTables = tableRepository.findByTournamentAndStage(tournamentId, stageId)
        val activePoolUsage = refreshedTables.count(_.status != TableStatus.Archived)
        val availablePoolSlots = math.max(0, preparedStage.schedulingPoolSize - activePoolUsage)

        val materializedTables =
          if availablePoolSlots <= 0 || preparedStage.pendingTablePlans.isEmpty then Vector.empty
          else
            val plansToMaterialize = preparedStage.pendingTablePlans.take(availablePoolSlots)
            val createdTables = plansToMaterialize.map { plan =>
              tableRepository.save(
                Table(
                  id = IdGenerator.tableId(),
                  tableNo = plan.tableNo,
                  tournamentId = tournamentId,
                  stageId = stageId,
                  seats = plan.seats,
                  stageRoundNumber = plan.roundNumber
                )
              )
            }

            val updatedTournament = preparedTournament
              .activateStage(stageId)
              .updateStage(stageId, _.consumePendingPlans(plansToMaterialize, createdTables.map(_.id)))
            tournamentRepository.save(
              if updatedTournament.status == TournamentStatus.RegistrationOpen then updatedTournament.markScheduled
              else updatedTournament
            )
            createdTables

        if materializedTables.nonEmpty || existingTables.nonEmpty || preparedStage.pendingTablePlans.nonEmpty then
          tableRepository.findByTournamentAndStage(tournamentId, stageId).sortBy(table =>
            (table.stageRoundNumber, table.tableNo, table.id.value)
          )
        else Vector.empty
    }

  protected def stageStandings(
      tournamentId: TournamentId,
      stageId: TournamentStageId,
      at: Instant = Instant.now()
  ): StageRankingSnapshot =
    stageQueries.stageStandings(tournamentId, stageId, at)

  protected def stageAdvancementPreview(
      tournamentId: TournamentId,
      stageId: TournamentStageId,
      at: Instant = Instant.now()
  ): StageAdvancementSnapshot =
    stageQueries.stageAdvancementPreview(tournamentId, stageId, at)

  protected def stageKnockoutBracket(
      tournamentId: TournamentId,
      stageId: TournamentStageId,
      at: Instant = Instant.now()
  ): KnockoutBracketSnapshot =
    stageQueries.stageKnockoutBracket(tournamentId, stageId, at)

  def advanceKnockoutStage(
      tournamentId: TournamentId,
      stageId: TournamentStageId,
      actor: AccessPrincipal,
      at: Instant = Instant.now()
  ): Vector[Table] =
    transactionManager.inTransaction {
      val tournament = tournamentRepository
        .findById(tournamentId)
        .getOrElse(throw NoSuchElementException(s"Tournament ${tournamentId.value} was not found"))
      val stage = requireStage(tournament, stageId)

      authorizationService.requirePermission(
        actor,
        Permission.ManageTournamentStages,
        tournamentId = Some(tournamentId)
      )

      val isKnockoutStage =
        stage.format == StageFormat.Knockout ||
          stage.format == StageFormat.Finals ||
          stage.advancementRule.ruleType == AdvancementRuleType.KnockoutElimination

      if !isKnockoutStage then
        throw IllegalArgumentException(
          s"Stage ${stageId.value} is not configured as a knockout stage"
        )

      knockoutStageCoordinator.materializeUnlockedTables(tournamentId, stageId, at)
    }

  def completeStage(
      tournamentId: TournamentId,
      stageId: TournamentStageId,
      actor: AccessPrincipal,
      completedAt: Instant = Instant.now()
  ): Option[StageAdvancementSnapshot] =
    transactionManager.inTransaction {
      tournamentRepository.findById(tournamentId).map { tournament =>
        authorizationService.requirePermission(
          actor,
          Permission.ManageTournamentStages,
          tournamentId = Some(tournamentId)
        )

        val stage = requireStage(tournament, stageId)
        val stageTables = tableRepository.findByTournamentAndStage(tournamentId, stageId)
        val isKnockoutStage =
          stage.format == StageFormat.Knockout ||
            stage.format == StageFormat.Finals ||
            stage.advancementRule.ruleType == AdvancementRuleType.KnockoutElimination

        if stageTables.size != stage.scheduledTableIds.size then
          throw IllegalArgumentException(
            s"Stage ${stageId.value} cannot complete before every scheduled table is materialized"
          )

        if stageTables.exists(_.status != TableStatus.Archived) then
          throw IllegalArgumentException(
            s"Stage ${stageId.value} cannot complete while tables are still active or under appeal"
          )

        val context = stageComputationContext(tournamentId, stageId, Some(tournament), Some(stage))
        if !isKnockoutStage then
          val effectiveRoundLimit = StageLineupSupport.effectiveRoundLimit(stage)
          val requiredTablesPerRound =
            expectedTablesPerRound(
              tournament = context.tournament,
              stage = context.stage,
              participants = context.participants,
              records = context.records,
              at = completedAt
            )
          val roundCounts = stageTables.groupBy(_.stageRoundNumber).view.mapValues(_.size).toMap
          val missingRounds = (1 to effectiveRoundLimit).filter(roundNumber =>
            roundCounts.getOrElse(roundNumber, 0) != requiredTablesPerRound
          )

          if stage.pendingTablePlans.nonEmpty || stage.currentRound < effectiveRoundLimit || missingRounds.nonEmpty then
            throw IllegalArgumentException(
              s"Stage ${stageId.value} cannot complete before all $effectiveRoundLimit rounds are fully scheduled and archived"
            )

        val ranking =
          tournamentRuleEngine.buildStageRanking(
            context.tournament,
            context.stage,
            context.participants.map(_.id),
            context.records,
            completedAt
          )
        val advancement = tournamentRuleEngine.projectAdvancement(context.tournament, context.stage, ranking, completedAt)

        tournamentRepository.save(tournament.updateStage(stageId, _.complete))
        advancement
      }
    }

