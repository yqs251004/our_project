package riichinexus.microservices.opsanalytics.objects

import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.microservices.club.objects.clubmanagement.ClubId

/** DashboardOwner 枚举仪表盘归属方 可使用的公开取值。 */

enum DashboardOwner:
  case Player(playerId: PlayerId)
  case Club(clubId: ClubId)

object DashboardOwner:

  def toString(owner: DashboardOwner): String =
    owner match
      case DashboardOwner.Player(playerId) => s"player:${playerId.value}"
      case DashboardOwner.Club(clubId)     => s"club:${clubId.value}"

  def fromString(value: String): Either[String, DashboardOwner] =
    value.trim.split(":", 2).toList match
      case "player" :: playerId :: Nil => Right(DashboardOwner.Player(PlayerId(playerId)))
      case "club" :: clubId :: Nil     => Right(DashboardOwner.Club(ClubId(clubId)))
      case _                           => Left(s"Unsupported DashboardOwner value: $value")
