package riichinexus.microservices.tournament.objects.apiTypes

import upickle.default.*

import riichinexus.domain.model.{ClubId, PlayerId}
import riichinexus.microservices.player.objects.{PlayerStatus, RankSnapshot}
import riichinexus.microservices.tournament.objects.RankSnapshotView

final case class TournamentParticipantPlayerView(
    playerId: String,
    nickname: String,
    status: String,
    elo: Int,
    currentRank: RankSnapshotView,
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
      currentRank = RankSnapshotView.fromDomain(currentRank),
      clubIds = clubIds.map(_.value)
    )
