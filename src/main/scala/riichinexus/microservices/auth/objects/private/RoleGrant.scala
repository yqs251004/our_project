package riichinexus.microservices.auth.objects.`private`

import java.time.Instant

import riichinexus.microservices.auth.objects.Role
import riichinexus.microservices.club.objects.clubmanagement.ClubId
import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.microservices.tournament.objects.tournamentmanagement.TournamentId

/** RoleGrant 表示后端内部 API 使用的角色Grant 数据载体，包含角色、grantedAt、grantedBy、俱乐部 ID、赛事 ID。 */

final case class RoleGrant(
    role: Role,
    grantedAt: Instant,
    grantedBy: Option[PlayerId] = None,
    clubId: Option[ClubId] = None,
    tournamentId: Option[TournamentId] = None
)
