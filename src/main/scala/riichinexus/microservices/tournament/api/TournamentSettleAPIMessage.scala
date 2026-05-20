package riichinexus.microservices.tournament.api

import java.time.Instant
import java.util.NoSuchElementException

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.domain.event.*
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

final case class TournamentSettleAPIMessage(tournamentId: String, request: SettleTournamentRequest) extends APIMessage[TournamentSettlementView] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[TournamentSettlementView] =
    IO {
      val module = context.support.tournamentModule
      val tournamentIdValue = TournamentId(tournamentId)
      val actor = context.support.principal(request.operator)
      val settledAt = Instant.now()

      TournamentSettlementView.fromDomain(
        module.transactionManager.inTransaction {
          require(request.prizePool >= 0L, "Prize pool must be non-negative")
          require(request.houseFeeAmount >= 0L, "House fee amount must be non-negative")
          require(request.houseFeeAmount <= request.prizePool, "House fee amount cannot exceed prize pool")
          require(
            request.clubShareRatio >= 0.0 && request.clubShareRatio <= 1.0,
            "Club share ratio must be between 0.0 and 1.0"
          )

          val tournament = module.tournamentRepository
            .findById(tournamentIdValue)
            .getOrElse(throw NoSuchElementException(s"Tournament ${tournamentIdValue.value} was not found"))
          val finalStage = tournament.stages
            .find(_.id == request.stageId)
            .getOrElse(throw NoSuchElementException(s"Stage ${request.stageId.value} was not found"))

          module.authorizationService.requirePermission(
            actor,
            Permission.ManageTournamentStages,
            tournamentId = Some(tournamentIdValue)
          )

          val ranking = module.stageQueries.stageStandings(tournamentIdValue, request.stageId, settledAt)
          val isKnockoutStage =
            finalStage.format == StageFormat.Knockout ||
              finalStage.format == StageFormat.Finals ||
              finalStage.advancementRule.ruleType == AdvancementRuleType.KnockoutElimination

          val resolvedPlayers =
            if isKnockoutStage then
              val bracket = module.stageQueries.stageKnockoutBracket(tournamentIdValue, request.stageId, settledAt)
              val championshipFinal = bracket.rounds
                .flatMap(_.matches)
                .find(matchNode => matchNode.lane == KnockoutLane.Championship && matchNode.nextMatchId.isEmpty)
                .getOrElse {
                  throw IllegalArgumentException(s"Stage ${request.stageId.value} does not contain a championship final")
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
                  s"Bronze match must be completed before settlement for stage ${request.stageId.value}"
                )
              if finalStage.knockoutRule.exists(_.repechageEnabled) && repechageFinal.exists(!_.completed) then
                throw IllegalArgumentException(
                  s"Repechage final must be completed before settlement for stage ${request.stageId.value}"
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
            if request.payoutRatios.nonEmpty then request.payoutRatios
            else RuntimeDictionary.currentSettlementPayoutRatios(module.globalDictionaryRepository)
          val netPrizePool = request.prizePool - request.houseFeeAmount
          val baseAwards = allocatePrizePool(netPrizePool, effectivePayoutRatios, resolvedPlayers.size)
          val rankingByPlayer = ranking.entries.map(entry => entry.playerId -> entry).toMap
          val adjustments = request.adjustments.map(_.adjustment)
          val adjustmentsByPlayer = adjustments.groupBy(_.playerId)
          val championId = resolvedPlayers.headOption.getOrElse {
            throw IllegalArgumentException(s"Stage ${request.stageId.value} does not contain any ranked players")
          }
          val previousSnapshot =
            module.tournamentSettlementRepository.findByTournamentAndStage(tournamentIdValue, request.stageId)
          previousSnapshot
            .filter(_.status != TournamentSettlementStatus.Superseded)
            .foreach(existing => module.tournamentSettlementRepository.save(existing.supersede(settledAt)))

          if tournament.stages.forall(_.status == StageStatus.Completed) && tournament.status != TournamentStatus.Completed then
            module.tournamentRepository.save(tournament.complete)

          val snapshot = TournamentSettlementSnapshot(
            id = IdGenerator.settlementSnapshotId(),
            tournamentId = tournamentIdValue,
            stageId = request.stageId,
            revision = previousSnapshot.map(_.revision + 1).getOrElse(1),
            status =
              if request.finalizeSettlement then TournamentSettlementStatus.Finalized
              else TournamentSettlementStatus.Draft,
            generatedAt = settledAt,
            finalizedAt = if request.finalizeSettlement then Some(settledAt) else None,
            supersedesSettlementId = previousSnapshot.map(_.id),
            championId = championId,
            prizePool = request.prizePool,
            houseFeeAmount = request.houseFeeAmount,
            netPrizePool = netPrizePool,
            clubShareRatio = request.clubShareRatio,
            adjustments = adjustments,
            entries = resolvedPlayers.zipWithIndex.map { case (playerId, index) =>
              val standing = rankingByPlayer.getOrElse(
                playerId,
                StageStandingEntry(playerId, 0, 0, 0, 0, 99.0)
              )
              val adjustmentAmount =
                adjustmentsByPlayer.getOrElse(playerId, Vector.empty).filter(_.amount > 0L).map(_.amount).sum
              val deductionAmount =
                adjustmentsByPlayer.getOrElse(playerId, Vector.empty).filter(_.amount < 0L).map(adjustment => math.abs(adjustment.amount)).sum
              val netAwardAmount = baseAwards.lift(index).getOrElse(0L) + adjustmentAmount - deductionAmount
              val clubId = module.playerRepository.findById(playerId).flatMap(_.boundClubIds.headOption)
              val clubShareAmount =
                if clubId.nonEmpty then math.floor(netAwardAmount.toDouble * request.clubShareRatio).toLong
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
              s"Champion ${championId.value} settled from stage ${request.stageId.value} " +
                s"(revision ${previousSnapshot.map(_.revision + 1).getOrElse(1)}, status ${if request.finalizeSettlement then "finalized" else "draft"}) " +
                s"with gross pool ${request.prizePool} and net pool $netPrizePool."
          )

          val savedSnapshot = module.tournamentSettlementRepository.save(snapshot)
          module.auditEventRepository.save(
            AuditEventEntry(
              id = IdGenerator.auditEventId(),
              aggregateType = "tournament",
              aggregateId = tournamentIdValue.value,
              eventType = "TournamentSettlementRecorded",
              occurredAt = settledAt,
              actorId = actor.playerId,
              details = Map(
                "stageId" -> request.stageId.value,
                "championId" -> championId.value,
                "prizePool" -> request.prizePool.toString,
                "netPrizePool" -> netPrizePool.toString,
                "houseFeeAmount" -> request.houseFeeAmount.toString,
                "clubShareRatio" -> request.clubShareRatio.toString,
                "revision" -> savedSnapshot.revision.toString,
                "status" -> savedSnapshot.status.toString
              ),
              note = request.note.orElse(Some(savedSnapshot.summary))
            )
          )
          module.eventBus.publish(TournamentSettlementRecorded(savedSnapshot, settledAt))
          savedSnapshot
        }
      )
    }

  private def allocatePrizePool(
      prizePool: Long,
      payoutRatios: Vector[Double],
      participantCount: Int
  ): Vector[Long] =
    if prizePool <= 0L || participantCount <= 0 then Vector.fill(participantCount)(0L)
    else
      val normalizedRatios =
        if payoutRatios.isEmpty then Vector(1.0)
        else payoutRatios.map(ratio => math.max(0.0, ratio))

      val ratioSum = normalizedRatios.sum
      val effectiveRatios =
        if ratioSum <= 0.0 then Vector(1.0)
        else normalizedRatios.map(_ / ratioSum)

      val paidSlots = math.min(participantCount, effectiveRatios.size)
      val baseAwards = effectiveRatios.take(paidSlots).map(ratio => math.floor(prizePool.toDouble * ratio).toLong)
      val remainder = prizePool - baseAwards.sum
      val adjustedAwards =
        if baseAwards.isEmpty then Vector.empty
        else baseAwards.updated(0, baseAwards.head + remainder)

      adjustedAwards ++ Vector.fill(participantCount - paidSlots)(0L)
