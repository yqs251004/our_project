package riichinexus.microservices.auth.domain.model

import java.time.Instant

import riichinexus.microservices.player.objects.playerprofile.PlayerId

import riichinexus.system.json.JsonCodecs.given
/** AuthenticatedSession 表示后端领域中的Authenticated会话 状态，包含令牌、用户名、玩家 ID、创建时间、过期时间、lastSeenAt等。 */
final case class AuthenticatedSession(
    token: String,
    username: String,
    playerId: PlayerId,
    createdAt: Instant,
    expiresAt: Instant,
    lastSeenAt: Option[Instant] = None,
    revokedAt: Option[Instant] = None,
    version: Int = 0
)