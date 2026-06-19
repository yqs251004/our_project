package riichinexus.microservices.tournament.mahjongcore.domain.gamestate.model

import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.microservices.tournament.objects.paifu.PaifuTile

import riichinexus.system.json.JsonCodecs.given
/** 后端内部的鸣牌/荣和响应窗口状态，记录某张弃牌后所有仍未处理的候选响应。 */
final case class MahjongPendingCallState(
    discardSequenceNo: Int,
    discardPlayerId: PlayerId,
    tile: PaifuTile,
    candidates: Vector[MahjongCallCandidate],
    acceptedRonPlayerIds: Vector[PlayerId] = Vector.empty,
    responses: Vector[MahjongCallResponse] = Vector.empty
)