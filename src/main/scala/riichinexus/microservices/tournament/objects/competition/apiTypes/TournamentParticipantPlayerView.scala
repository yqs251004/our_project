package riichinexus.microservices.tournament.objects.competition.apiTypes

import upickle.default.{ReadWriter, macroRW}

import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.microservices.club.objects.clubmanagement.ClubId
import riichinexus.system.json.JsonCodecs.given
import riichinexus.microservices.player.objects.{PlayerStatus, RankSnapshot}

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

  def apply(
      playerId: PlayerId,
      nickname: String,
      status: PlayerStatus,
      elo: Int,
      currentRank: RankSnapshot,
      clubIds: Vector[ClubId]
  ): TournamentParticipantPlayerView =
    TournamentParticipantPlayerView(
      playerId = playerId.value,
      nickname = nickname,
      status = status.toString,
      elo = elo,
      currentRank = currentRank,
      clubIds = clubIds.map(_.value)
    )
