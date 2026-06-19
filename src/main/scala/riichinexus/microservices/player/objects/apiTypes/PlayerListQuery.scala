package riichinexus.microservices.player.objects.apiTypes
import riichinexus.microservices.club.objects.clubmanagement.ClubId
import riichinexus.microservices.player.objects.PlayerStatus

/** PlayerListQuery 表示玩家列表查询 的列表或详情查询条件，包含俱乐部 ID、状态、昵称、数量限制、分页偏移。 */

final case class PlayerListQuery(
    clubId: Option[ClubId] = None,
    status: Option[PlayerStatus] = None,
    nickname: Option[String] = None,
    limit: Option[Int] = None,
    offset: Option[Int] = None
)
