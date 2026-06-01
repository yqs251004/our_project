package riichinexus.microservices.tournament.objects.tournamentmanagement.apiTypes

import upickle.default.*

import riichinexus.domain.model.{ClubId, PlayerId}
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.player.objects.{PlayerStatus, RankSnapshot}

final case class TournamentParticipantPlayerView(
    playerId: String,
    nickname: String,
    status: String,
    elo: Int,
    currentRank: RankSnapshot,
    clubIds: Vector[String]
) derives CanEqual

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
