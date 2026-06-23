package riichinexus.microservices.auth.domain.session.model

import java.time.Instant

import riichinexus.microservices.player.objects.PlayerId
import riichinexus.microservices.auth.objects.session.GuestSessionId

import riichinexus.system.json.JsonCodecs.given

/** 游客身份在注册或过期前使用的临时访问会话。
  *
  * 它记录显示名、设备指纹、有效期、撤销信息和升级后的玩家 ID，使大厅体验可以先以游客身份进入，之后再平滑绑定正式玩家。
  */
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
