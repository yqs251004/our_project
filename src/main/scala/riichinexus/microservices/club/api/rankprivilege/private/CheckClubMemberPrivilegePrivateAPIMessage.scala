package riichinexus.microservices.club.api.rankprivilege.`private`
import cats.effect.IO
import riichinexus.microservices.club.api.profile.`private`.ResolveClubPrivateAPIMessage
import riichinexus.microservices.club.domain.profile.functions.ClubFunctions
import riichinexus.microservices.club.objects.profile.ClubId
import riichinexus.microservices.club.objects.rankprivilege.ClubPrivilegeCode
import riichinexus.microservices.player.objects.PlayerId
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.system.json.JsonCodecs.given
/** 供后端服务检查玩家是否拥有俱乐部内委托权限。 */
final case class CheckClubMemberPrivilegePrivateAPIMessage(
    clubId: ClubId,
    playerId: PlayerId,
    privilege: ClubPrivilegeCode
) extends APIMessage[Boolean]:

  override def plan(context: ApiPlanContext): IO[Boolean] =
    ResolveClubPrivateAPIMessage(clubId).plan(context).map {
      case Some(club) =>
        club.members.contains(playerId) && ClubFunctions.hasPrivilege(club, playerId, privilege)
      case None =>
        false
    }
