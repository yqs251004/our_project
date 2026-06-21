package riichinexus.microservices.auth.domain.model

import java.time.Instant

import riichinexus.microservices.player.objects.playerprofile.PlayerId

import riichinexus.system.json.JsonCodecs.given

/** 后端保存的账号密码凭证。
  *
  * 该类型绑定用户名和玩家档案，并保存哈希、盐值、迭代次数及版本号；明文密码只在请求层短暂存在，不会进入这个领域状态。
  */
final case class AccountCredential(
    username: String,
    playerId: PlayerId,
    passwordHash: String,
    passwordSalt: String,
    passwordIterations: Int,
    createdAt: Instant,
    updatedAt: Instant,
    version: Int = 0
)
