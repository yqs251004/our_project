package riichinexus.microservices.tournament.objects.competition

import upickle.default.{ReadWriter, macroRW}

import riichinexus.system.json.JsonCodecs.given
import riichinexus.microservices.player.objects.RankSnapshot

/** 赛事详情中展示的参赛玩家摘要。
  *
  * 视图携带昵称、账号状态、Elo、当前段位和俱乐部归属，便于运营后台筛选参赛者并安排阶段阵容或牌桌。
  */
final case class TournamentParticipantPlayerView(
    playerId: String,
    nickname: String,
    status: String,
    elo: Int,
    currentRank: RankSnapshot,
    clubIds: Vector[String]
)

object TournamentParticipantPlayerView:
  given ReadWriter[TournamentParticipantPlayerView] = macroRW
