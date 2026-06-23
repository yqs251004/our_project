package riichinexus.microservices.auth.objects.authorization.`private`

import java.time.Instant

import riichinexus.microservices.auth.objects.authorization.Role
import riichinexus.microservices.club.objects.profile.ClubId
import riichinexus.microservices.player.objects.PlayerId
import riichinexus.microservices.tournament.objects.identity.TournamentId

/** 一次角色授予记录及其可选作用域。
  *
  * 俱乐部管理员和赛事管理员会绑定到对应资源，平台角色则保持全局；
  * `grantedBy` 和 `grantedAt` 用于审计谁在何时赋予了这项访问能力。
  */
final case class RoleGrant(
    role: Role,
    grantedAt: Instant,
    grantedBy: Option[PlayerId] = None,
    clubId: Option[ClubId] = None,
    tournamentId: Option[TournamentId] = None
)
