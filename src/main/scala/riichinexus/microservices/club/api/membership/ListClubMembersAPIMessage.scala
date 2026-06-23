package riichinexus.microservices.club.api.membership
import cats.effect.IO
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.microservices.club.objects.profile.ClubId
import riichinexus.system.json.JsonCodecs.given
import riichinexus.microservices.player.objects.PlayerStatus
import riichinexus.microservices.player.objects.PlayerProfileView
import riichinexus.microservices.player.api.ListPlayersAPIMessage
import riichinexus.system.objects.{PagedResponse, QueryFilterField}
/** 列出俱乐部成员。 */
final case class ListClubMembersAPIMessage(
    clubId: String,
    status: Option[String] = None,
    nickname: Option[String] = None,
    limit: Option[Int] = None,
    offset: Option[Int] = None
) extends APIMessage[PagedResponse[PlayerProfileView]]:

  override def plan(context: ApiPlanContext): IO[PagedResponse[PlayerProfileView]] =
    val requestedClubId = ClubId(clubId)
    val statusFilter = status.filter(_.nonEmpty).map(
      riichinexus.system.EnumParsing.parse(QueryFilterField.toString(QueryFilterField.Status), _)(PlayerStatus.valueOf)
    )
    val nicknameFilter = nickname.filter(_.nonEmpty)
    val resolvedLimit = limit.getOrElse(20)
    val resolvedOffset = offset.getOrElse(0)
    for
      members <- ListPlayersAPIMessage(
        clubId = Some(requestedClubId.value),
        status = statusFilter.map(_.toString),
        nickname = nicknameFilter,
        limit = Some(resolvedLimit),
        offset = Some(resolvedOffset)
      ).plan(context)
    yield members
