package riichinexus.microservices.opsanalytics.objects

import riichinexus.microservices.player.objects.PlayerId
import riichinexus.microservices.club.objects.profile.ClubId

/** 运营分析看板所属的业务主体。
  *
  * 看板既可以归属于单个玩家，也可以归属于俱乐部；序列化格式带有前缀，避免不同 ID 空间发生冲突。
  */
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
