package riichinexus.microservices.tournament.api

import java.time.Instant
import java.util.NoSuchElementException

import riichinexus.application.ports.*
import riichinexus.domain.event.*
import riichinexus.domain.model.*
import riichinexus.domain.service.*
import riichinexus.microservices.dictionary.api.RuntimeDictionarySupport

private[tournament] trait TournamentSettlementWorkflow extends TournamentWorkflowSupport:
  self: TournamentStageWorkflow =>
  def settleTournament(
      tournamentId: TournamentId,
      finalStageId: TournamentStageId,
      prizePool: Long,
      payoutRatios: Vector[Double] = Vector.empty,
      houseFeeAmount: Long = 0L,
      clubShareRatio: Double = 0.0,
      adjustments: Vector[TournamentSettlementAdjustment] = Vector.empty,
      finalize: Boolean = true,
      note: Option[String] = None,
      actor: AccessPrincipal,
      settledAt: Instant = Instant.now()
  ): TournamentSettlementSnapshot =
    transactionManager.inTransaction {
      require(prizePool >= 0L, "Prize pool must be non-negative")
      require(houseFeeAmount >= 0L, "House fee amount must be non-negative")
      require(houseFeeAmount <= prizePool, "House fee amount cannot exceed prize pool")
      require(clubShareRatio >= 0.0 && clubShareRatio <= 1.0, "Club share ratio must be between 0.0 and 1.0")

      val tournament = tournamentRepository
        .findById(tournamentId)
        .getOrElse(throw NoSuchElementException(s"Tournament ${tournamentId.value} was not found"))
      val finalStage = requireStage(tournament, finalStageId)

      authorizationService.requirePermission(
        actor,
        Permission.ManageTournamentStages,
        tournamentId = Some(tournamentId)
      )

      val ranking = stageStandings(tournamentId, finalStageId, settledAt)
      val isKnockoutStage =
        finalStage.format == StageFormat.Knockout ||
          finalStage.format == StageFormat.Finals ||
          finalStage.advancementRule.ruleType == AdvancementRuleType.KnockoutElimination

      val resolvedPlayers =
        if isKnockoutStage then
          val bracket = stageKnockoutBracket(tournamentId, finalStageId, settledAt)
          val championshipFinal = bracket.rounds
            .flatMap(_.matches)
            .find(matchNode => matchNode.lane == KnockoutLane.Championship && matchNode.nextMatchId.isEmpty)
            .getOrElse {
              throw IllegalArgumentException(s"Stage ${finalStageId.value} does not contain a championship final")
            }
          if !championshipFinal.completed then
            throw IllegalArgumentException(
              s"Final knockout match ${championshipFinal.id} must be completed before settlement"
            )

          val bronzeMatch = bracket.rounds
            .flatMap(_.matches)
            .find(_.lane == KnockoutLane.Bronze)
          val repechageFinal = bracket.rounds
            .flatMap(_.matches)
            .filter(_.lane == KnockoutLane.Repechage)
            .find(_.nextMatchId.isEmpty)

          if finalStage.knockoutRule.exists(_.thirdPlaceMatch) && bronzeMatch.exists(!_.completed) then
            throw IllegalArgumentException(
              s"Bronze match must be completed before settlement for stage ${finalStageId.value}"
            )
          if finalStage.knockoutRule.exists(_.repechageEnabled) && repechageFinal.exists(!_.completed) then
            throw IllegalArgumentException(
              s"Repechage final must be completed before settlement for stage ${finalStageId.value}"
            )

          val championshipPlayers = championshipFinal.results.sortBy(_.placement).map(_.playerId)
          val bronzePlayers = bronzeMatch.toVector.flatMap { matchNode =>
            if !matchNode.completed then Vector.empty
            else matchNode.results.sortBy(_.placement).map(_.playerId)
          }
          val repechagePlayers = repechageFinal.toVector.flatMap { matchNode =>
            if !matchNode.completed then Vector.empty
            else matchNode.results.sortBy(_.placement).map(_.playerId)
          }
          championshipPlayers ++ bronzePlayers ++ repechagePlayers ++
            ranking.entries.map(_.playerId).filterNot(playerId =>
              (championshipPlayers ++ bronzePlayers ++ repechagePlayers).contains(playerId)
            )
        else ranking.entries.map(_.playerId)

      val effectivePayoutRatios =
        if payoutRatios.nonEmpty then payoutRatios
        else RuntimeDictionarySupport.currentSettlementPayoutRatios(globalDictionaryRepository)
      val netPrizePool = prizePool - houseFeeAmount
      val baseAwards = allocatePrizePool(netPrizePool, effectivePayoutRatios, resolvedPlayers.size)
      val rankingByPlayer = ranking.entries.map(entry => entry.playerId -> entry).toMap
      val adjustmentsByPlayer = adjustments.groupBy(_.playerId)
      val championId = resolvedPlayers.headOption.getOrElse {
        throw IllegalArgumentException(s"Stage ${finalStageId.value} does not contain any ranked players")
      }
      val previousSnapshot = tournamentSettlementRepository.findByTournamentAndStage(tournamentId, finalStageId)
      previousSnapshot
        .filter(_.status != TournamentSettlementStatus.Superseded)
        .foreach(existing => tournamentSettlementRepository.save(existing.supersede(settledAt)))

      if tournament.stages.forall(_.status == StageStatus.Completed) && tournament.status != TournamentStatus.Completed then
        tournamentRepository.save(tournament.complete)

      val snapshot = TournamentSettlementSnapshot(
        id = IdGenerator.settlementSnapshotId(),
        tournamentId = tournamentId,
        stageId = finalStageId,
        revision = previousSnapshot.map(_.revision + 1).getOrElse(1),
        status = if finalize then TournamentSettlementStatus.Finalized else TournamentSettlementStatus.Draft,
        generatedAt = settledAt,
        finalizedAt = if finalize then Some(settledAt) else None,
        supersedesSettlementId = previousSnapshot.map(_.id),
        championId = championId,
        prizePool = prizePool,
        houseFeeAmount = houseFeeAmount,
        netPrizePool = netPrizePool,
        clubShareRatio = clubShareRatio,
        adjustments = adjustments,
        entries = resolvedPlayers.zipWithIndex.map { case (playerId, index) =>
          val standing = rankingByPlayer.getOrElse(
            playerId,
            StageStandingEntry(playerId, 0, 0, 0, 0, 99.0)
          )
          val adjustmentAmount = adjustmentsByPlayer.getOrElse(playerId, Vector.empty).filter(_.amount > 0L).map(_.amount).sum
          val deductionAmount = adjustmentsByPlayer.getOrElse(playerId, Vector.empty).filter(_.amount < 0L).map(adjustment => math.abs(adjustment.amount)).sum
          val netAwardAmount = baseAwards.lift(index).getOrElse(0L) + adjustmentAmount - deductionAmount
          val clubId = playerRepository.findById(playerId).flatMap(_.boundClubIds.headOption)
          val clubShareAmount =
            if clubId.nonEmpty then math.floor(netAwardAmount.toDouble * clubShareRatio).toLong
            else 0L
          TournamentSettlementEntry(
            playerId = playerId,
            rank = index + 1,
            awardAmount = netAwardAmount,
            baseAwardAmount = baseAwards.lift(index).getOrElse(0L),
            adjustmentAmount = adjustmentAmount,
            deductionAmount = deductionAmount,
            clubId = clubId,
            clubShareAmount = math.max(0L, clubShareAmount),
            playerRetainedAmount = netAwardAmount - math.max(0L, clubShareAmount),
            finalPoints = standing.totalFinalPoints,
            champion = index == 0
          )
        },
        summary =
          s"Champion ${championId.value} settled from stage ${finalStageId.value} " +
            s"(revision ${previousSnapshot.map(_.revision + 1).getOrElse(1)}, status ${if finalize then "finalized" else "draft"}) " +
            s"with gross pool $prizePool and net pool $netPrizePool."
      )

      val savedSnapshot = tournamentSettlementRepository.save(snapshot)
      auditEventRepository.save(
        AuditEventEntry(
          id = IdGenerator.auditEventId(),
          aggregateType = "tournament",
          aggregateId = tournamentId.value,
          eventType = "TournamentSettlementRecorded",
          occurredAt = settledAt,
          actorId = actor.playerId,
          details = Map(
            "stageId" -> finalStageId.value,
            "championId" -> championId.value,
            "prizePool" -> prizePool.toString,
            "netPrizePool" -> netPrizePool.toString,
            "houseFeeAmount" -> houseFeeAmount.toString,
            "clubShareRatio" -> clubShareRatio.toString,
            "revision" -> savedSnapshot.revision.toString,
            "status" -> savedSnapshot.status.toString
          ),
          note = note.orElse(Some(savedSnapshot.summary))
        )
      )
      eventBus.publish(TournamentSettlementRecorded(savedSnapshot, settledAt))
      savedSnapshot
    }

  def finalizeTournamentSettlement(
      tournamentId: TournamentId,
      settlementId: SettlementSnapshotId,
      actor: AccessPrincipal,
      note: Option[String] = None,
      finalizedAt: Instant = Instant.now()
  ): Option[TournamentSettlementSnapshot] =
    transactionManager.inTransaction {
      authorizationService.requirePermission(
        actor,
        Permission.ManageTournamentStages,
        tournamentId = Some(tournamentId)
      )

      tournamentSettlementRepository.findById(settlementId)
        .filter(_.tournamentId == tournamentId)
        .map { settlement =>
          val finalized =
            if settlement.status == TournamentSettlementStatus.Finalized then settlement
            else tournamentSettlementRepository.save(settlement.finalize(finalizedAt))
          auditEventRepository.save(
            AuditEventEntry(
              id = IdGenerator.auditEventId(),
              aggregateType = "tournament",
              aggregateId = tournamentId.value,
              eventType = "TournamentSettlementFinalized",
              occurredAt = finalizedAt,
              actorId = actor.playerId,
              details = Map(
                "stageId" -> finalized.stageId.value,
                "settlementId" -> finalized.id.value,
                "revision" -> finalized.revision.toString
              ),
              note = note.orElse(Some(s"Finalized settlement ${finalized.id.value}"))
            )
          )
          finalized
        }
    }

