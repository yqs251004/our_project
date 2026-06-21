package riichinexus.microservices.auth.domain.model

import java.time.Instant

import riichinexus.microservices.player.objects.playerprofile.PlayerId

import riichinexus.system.json.JsonCodecs.given

/** 已注册账号登录后的服务端会话。
  *
  * 会话令牌在这里和用户名、玩家 ID、有效期、最近访问时间、撤销时间一起保存，用于恢复登录状态和拒绝过期或已撤销的请求。
  */
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
