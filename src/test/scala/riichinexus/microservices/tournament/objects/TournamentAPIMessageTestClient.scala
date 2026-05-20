package riichinexus.microservices.tournament.objects

import java.time.Instant
import java.util.NoSuchElementException

import cats.effect.unsafe.implicits.global
import riichinexus.api.ApiPlanContext
import riichinexus.bootstrap.ApplicationContext
import riichinexus.domain.model.*
import riichinexus.microservices.tournament.api.*
import riichinexus.microservices.tournament.objects.apiTypes.*

final class TournamentAPIMessageTestClient(
    app: ApplicationContext,
    apiContext: ApiPlanContext
):
  def createTournament(
      name: String,
      organizer: String,
      startsAt: Instant,
      endsAt: Instant,
      stages: Vector[TournamentStage],
      adminId: Option[PlayerId] = None,
      actor: AccessPrincipal = AccessPrincipal.system
  ): Tournament =
    val response = TournamentCreateAPIMessage(
      name = name,
      organizer = organizer,
      startsAt = startsAt,
      endsAt = endsAt,
      stages = stages.map(stageRequest),
      adminId = adminId.map(_.value)
    ).plan(apiContext).unsafeRunSync()
    tournament(response.tournamentId)

  def registerPlayer(
      tournamentId: TournamentId,
      playerId: PlayerId,
      actor: AccessPrincipal = AccessPrincipal.system
  ): Option[Tournament] =
    TournamentRegisterPlayerAPIMessage(tournamentId.value, playerId.value, operatorId(actor))
      .plan(apiContext)
      .unsafeRunSync()
    app.tournamentModule.tournamentRepository.findById(tournamentId)

  def registerClub(
      tournamentId: TournamentId,
      clubId: ClubId,
      actor: AccessPrincipal = AccessPrincipal.system
  ): Option[Tournament] =
    TournamentRegisterClubAPIMessage(tournamentId.value, clubId.value, operatorId(actor))
      .plan(apiContext)
      .unsafeRunSync()
    app.tournamentModule.tournamentRepository.findById(tournamentId)

  def removeClubParticipation(
      tournamentId: TournamentId,
      clubId: ClubId,
      actor: AccessPrincipal = AccessPrincipal.system
  ): Option[Tournament] =
    TournamentRemoveClubParticipationAPIMessage(tournamentId.value, clubId.value, operatorId(actor))
      .plan(apiContext)
      .unsafeRunSync()
    app.tournamentModule.tournamentRepository.findById(tournamentId)

  def whitelistPlayer(
      tournamentId: TournamentId,
      playerId: PlayerId,
      actor: AccessPrincipal = AccessPrincipal.system
  ): Option[Tournament] =
    TournamentWhitelistPlayerAPIMessage(tournamentId.value, playerId.value, operatorId(actor))
      .plan(apiContext)
      .unsafeRunSync()
    app.tournamentModule.tournamentRepository.findById(tournamentId)

  def whitelistClub(
      tournamentId: TournamentId,
      clubId: ClubId,
      actor: AccessPrincipal = AccessPrincipal.system
  ): Option[Tournament] =
    TournamentWhitelistClubAPIMessage(tournamentId.value, clubId.value, operatorId(actor))
      .plan(apiContext)
      .unsafeRunSync()
    app.tournamentModule.tournamentRepository.findById(tournamentId)

  def publishTournament(
      tournamentId: TournamentId,
      actor: AccessPrincipal = AccessPrincipal.system
  ): Option[Tournament] =
    TournamentPublishAPIMessage(tournamentId.value, operatorId(actor))
      .plan(apiContext)
      .unsafeRunSync()
    app.tournamentModule.tournamentRepository.findById(tournamentId)

  def startTournament(
      tournamentId: TournamentId,
      actor: AccessPrincipal = AccessPrincipal.system
  ): Option[Tournament] =
    TournamentStartAPIMessage(tournamentId.value, operatorId(actor))
      .plan(apiContext)
      .unsafeRunSync()
    app.tournamentModule.tournamentRepository.findById(tournamentId)

  def assignTournamentAdmin(
      tournamentId: TournamentId,
      playerId: PlayerId,
      actor: AccessPrincipal,
      grantedAt: Instant = Instant.now()
  ): Option[Tournament] =
    TournamentAssignAdminAPIMessage(
      tournamentId.value,
      AssignTournamentAdminRequest(playerId.value, requiredOperator(actor).value)
    ).plan(apiContext).unsafeRunSync()
    app.tournamentModule.tournamentRepository.findById(tournamentId)

  def revokeTournamentAdmin(
      tournamentId: TournamentId,
      playerId: PlayerId,
      actor: AccessPrincipal
  ): Option[Tournament] =
    TournamentRevokeAdminAPIMessage(tournamentId.value, playerId.value, operatorId(actor))
      .plan(apiContext)
      .unsafeRunSync()
    app.tournamentModule.tournamentRepository.findById(tournamentId)

  def submitLineup(
      tournamentId: TournamentId,
      stageId: TournamentStageId,
      submission: StageLineupSubmission,
      actor: AccessPrincipal
  ): Option[Tournament] =
    TournamentStageSubmitLineupAPIMessage(
      tournamentId.value,
      stageId.value,
      SubmitStageLineupRequest(
        clubId = submission.clubId.value,
        operatorId = requiredOperator(actor).value,
        seats = submission.seats.map(seat =>
          StageLineupSeatRequest(
            playerId = seat.playerId.value,
            preferredWind = seat.preferredWind.map(_.toString),
            reserve = seat.reserve
          )
        ),
        note = submission.note
      )
    ).plan(apiContext).unsafeRunSync()
    app.tournamentModule.tournamentRepository.findById(tournamentId)

  def scheduleStageTables(
      tournamentId: TournamentId,
      stageId: TournamentStageId,
      actor: AccessPrincipal = AccessPrincipal.system
  ): Vector[Table] =
    TournamentStageScheduleTablesAPIMessage(tournamentId.value, stageId.value, operatorId(actor))
      .plan(apiContext)
      .unsafeRunSync()
      .scheduledTables
      .flatMap(view => app.tournamentModule.tableRepository.findById(view.tableId))

  def completeStage(
      tournamentId: TournamentId,
      stageId: TournamentStageId,
      actor: AccessPrincipal,
      completedAt: Instant = Instant.now()
  ): Option[StageAdvancementSnapshot] =
    Some(
      TournamentStageCompleteAPIMessage(
        tournamentId.value,
        stageId.value,
        CompleteStageRequest(actor.playerId.map(_.value))
      ).plan(apiContext).unsafeRunSync()
    )

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
    val response = TournamentSettleAPIMessage(
      tournamentId.value,
      SettleTournamentRequest(
        operatorId = requiredOperator(actor).value,
        finalStageId = finalStageId.value,
        prizePool = prizePool,
        payoutRatios = payoutRatios,
        houseFeeAmount = houseFeeAmount,
        clubShareRatio = clubShareRatio,
        adjustments = adjustments.map(adjustment =>
          SettlementAdjustmentRequest(
            playerId = adjustment.playerId.value,
            label = adjustment.label,
            amount = adjustment.amount,
            note = adjustment.note
          )
        ),
        finalizeSettlement = finalize,
        note = note
      )
    ).plan(apiContext).unsafeRunSync()
    app.tournamentModule.tournamentSettlementRepository.findById(response.settlementId)
      .getOrElse(throw NoSuchElementException(s"Settlement ${response.settlementId.value} was not found"))

  def finalizeTournamentSettlement(
      tournamentId: TournamentId,
      settlementId: SettlementSnapshotId,
      actor: AccessPrincipal,
      note: Option[String] = None,
      finalizedAt: Instant = Instant.now()
  ): Option[TournamentSettlementSnapshot] =
    val response = TournamentSettlementFinalizeAPIMessage(
      tournamentId.value,
      settlementId.value,
      FinalizeTournamentSettlementRequest(requiredOperator(actor).value, note)
    ).plan(apiContext).unsafeRunSync()
    app.tournamentModule.tournamentSettlementRepository.findById(response.settlementId)

  private def tournament(tournamentId: TournamentId): Tournament =
    app.tournamentModule.tournamentRepository.findById(tournamentId)
      .getOrElse(throw NoSuchElementException(s"Tournament ${tournamentId.value} was not found"))

  private def operatorId(actor: AccessPrincipal): Option[String] =
    actor.playerId.map(_.value)

  private def requiredOperator(actor: AccessPrincipal): PlayerId =
    actor.playerId.getOrElse(throw IllegalArgumentException("Test API client requires a player principal"))

  private def stageRequest(stage: TournamentStage): CreateTournamentStageRequest =
    CreateTournamentStageRequest(
      id = Some(stage.id.value),
      name = stage.name,
      format = stage.format.toString,
      order = stage.order,
      roundCount = stage.roundCount,
      ruleTemplateKey = stage.advancementRule.templateKey,
      advancementRuleType = Some(stage.advancementRule.ruleType.toString),
      cutSize = stage.advancementRule.cutSize,
      thresholdScore = stage.advancementRule.thresholdScore,
      targetTableCount = stage.advancementRule.targetTableCount,
      note = stage.advancementRule.note,
      pairingMethod = stage.swissRule.map(_.pairingMethod),
      carryOverPoints = stage.swissRule.map(_.carryOverPoints),
      maxRounds = stage.swissRule.flatMap(_.maxRounds),
      bracketSize = stage.knockoutRule.flatMap(_.bracketSize),
      thirdPlaceMatch = stage.knockoutRule.map(_.thirdPlaceMatch),
      repechageEnabled = stage.knockoutRule.map(_.repechageEnabled),
      seedingPolicy = stage.knockoutRule.map(_.seedingPolicy),
      schedulingPoolSize = Some(stage.schedulingPoolSize)
    )
