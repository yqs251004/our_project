package riichinexus.microservices.tournament.objects.tournamentmanagement.apiTypes

import upickle.default.{ReadWriter, macroRW}

import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.microservices.club.objects.clubmanagement.ClubId
import riichinexus.system.json.JsonCodecs.given
import riichinexus.microservices.player.objects.{PlayerStatus, RankSnapshot}

/** TournamentParticipantPlayerView 表示赛事参赛方玩家视图 的前端展示视图，包含玩家 ID、昵称、状态、elo、currentRank、clubIds等。 */

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
