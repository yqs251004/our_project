package riichinexus.microservices.club.objects.apiTypes

import riichinexus.domain.model.{ClubId, PlayerId}
import upickle.default.*

final case class PublicClubRecentMatchSeatView(
    playerId: String,
    nickname: String,
    clubId: Option[String],
    seat: String,
    placement: Int,
    scoreDelta: Int,
    finalPoints: Int
) derives CanEqual

object PublicClubRecentMatchSeatView:
  given ReadWriter[PublicClubRecentMatchSeatView] = macroRW

  def apply(
      playerId: PlayerId,
      nickname: String,
      clubId: Option[ClubId],
      seat: String,
      placement: Int,
      scoreDelta: Int,
      finalPoints: Int
  ): PublicClubRecentMatchSeatView =
    PublicClubRecentMatchSeatView(
      playerId = playerId.value,
      nickname = nickname,
      clubId = clubId.map(_.value),
      seat = seat,
      placement = placement,
      scoreDelta = scoreDelta,
      finalPoints = finalPoints
    )
