package riichinexus.microservices.player.objects.apiTypes

import riichinexus.domain.model.{ClubId, PlayerId}
import riichinexus.microservices.player.objects.PlayerStatus
import riichinexus.microservices.tournament.objects.apiTypes.RankSnapshotView
import upickle.default.*

final case class PlayerLeaderboardEntry(
    playerId: String,
    nickname: String,
    elo: Int,
    currentRank: RankSnapshotView,
    normalizedRankScore: Option[Int],
    clubIds: Vector[String],
    status: String
) derives CanEqual

object PlayerLeaderboardEntry:
  given ReadWriter[PlayerLeaderboardEntry] = macroRW

  def apply(
      playerId: PlayerId,
      nickname: String,
      elo: Int,
      currentRank: RankSnapshotView,
      normalizedRankScore: Option[Int],
      clubIds: Vector[ClubId],
      status: PlayerStatus
  ): PlayerLeaderboardEntry =
    PlayerLeaderboardEntry(
      playerId = playerId.value,
      nickname = nickname,
      elo = elo,
      currentRank = currentRank,
      normalizedRankScore = normalizedRankScore,
      clubIds = clubIds.map(_.value),
      status = status.toString
    )
