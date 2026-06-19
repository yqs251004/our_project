package riichinexus.microservices.auth.domain.model

import java.time.Instant

import riichinexus.microservices.player.objects.playerprofile.PlayerId

import riichinexus.system.json.JsonCodecs.given
/** AccountCredential 表示后端领域中的账号凭证 状态，包含用户名、玩家 ID、密码哈希、密码盐值、passwordIterations、创建时间等。 */
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