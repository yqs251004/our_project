package riichinexus.microservices.tournament.domain.finalization.model

import java.time.Instant

import riichinexus.microservices.auth.objects.authorization.`private`.AccessPrincipalPrivateView
import riichinexus.microservices.tournament.objects.finalization.TournamentSettlementAdjustment
import riichinexus.microservices.tournament.objects.identity.{TournamentId, TournamentStageId}

/** 赛事结算请求解析并完成授权后的内部输入。
  *
  * 结算协调器使用该模型在校验、排名读取、奖金分配和快照落库之间传递同一组结算参数。
  */
private[tournament] final case class TournamentSettlementInput(
    tournamentId: TournamentId,
    finalStageId: TournamentStageId,
    actor: AccessPrincipalPrivateView,
    settledAt: Instant,
    prizePool: Long,
    payoutRatios: Vector[Double],
    houseFeeAmount: Long,
    clubShareRatio: Double,
    adjustments: Vector[TournamentSettlementAdjustment],
    finalizeSettlement: Boolean,
    note: Option[String]
)
