package riichinexus.microservices.opsanalytics.domain.model

import riichinexus.microservices.player.objects.playerprofile.PlayerId

import riichinexus.system.json.JsonCodecs.given
/** RatingChange 表示后端领域中的评级变化 状态，包含玩家 ID、delta。 */
final case class RatingChange(
    playerId: PlayerId,
    delta: Int
)