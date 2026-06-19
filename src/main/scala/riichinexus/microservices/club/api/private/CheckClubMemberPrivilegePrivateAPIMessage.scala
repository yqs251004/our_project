package riichinexus.microservices.club.api.`private`

import cats.effect.IO
import riichinexus.microservices.club.domain.clubmanagement.functions.ClubFunctions
import riichinexus.microservices.club.objects.clubmanagement.ClubId
import riichinexus.microservices.club.objects.rankprivilegemanagement.ClubPrivilegeCode
import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.system.json.JsonCodecs.given
import upickle.default.ReadWriter

/** 供后端服务检查玩家是否拥有俱乐部内委托权限。 */
final case class CheckClubMemberPrivilegePrivateAPIMessage(
    clubId: ClubId,
    playerId: PlayerId,
    privilege: ClubPrivilegeCode
) extends APIMessage[Boolean] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[Boolean] =
    ResolveClubPrivateAPIMessage(clubId).plan(context).map {
      case Some(club) =>
        club.members.contains(playerId) && ClubFunctions.hasPrivilege(club, playerId, privilege)
      case None =>
        false
    }
