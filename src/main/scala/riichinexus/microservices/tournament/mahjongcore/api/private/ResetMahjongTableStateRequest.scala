package riichinexus.microservices.tournament.mahjongcore.api.`private`

import riichinexus.system.json.JsonCodecs.given
/** 后端重置实时麻将状态时使用的请求，通常由运营或申诉流程触发。 */
final case class ResetMahjongTableStateRequest(
    operatorId: Option[String] = None,
    note: String
)
