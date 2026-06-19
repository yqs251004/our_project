package riichinexus.microservices.auth.domain.model

import java.time.Instant

import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.microservices.auth.objects.sessionmanagement.GuestSessionId

import riichinexus.system.json.JsonCodecs.given
/** GuestAccessSession 表示后端领域中的游客Access会话 状态，包含 ID、创建时间、显示名、过期时间、lastSeenAt、revokedAt等。 */
final case class GuestAccessSession(
    id: GuestSessionId,
    createdAt: Instant,
    displayName: String = "guest",
    expiresAt: Instant,
    lastSeenAt: Option[Instant] = None,
    revokedAt: Option[Instant] = None,
    revokedReason: Option[String] = None,
    deviceFingerprint: Option[String] = None,
    upgradedToPlayerId: Option[PlayerId] = None,
    version: Int = 0
)