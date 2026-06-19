package riichinexus.microservices.tournament.domain.stage.model


import riichinexus.microservices.tournament.objects.tablemanagement.TableSeat

import riichinexus.system.json.JsonCodecs.given
/** StageTablePlan 表示后端领域中的阶段牌桌Plan状态或规则，包含roundNumber、tableNo、座位。 */
final case class StageTablePlan(
    roundNumber: Int,
    tableNo: Int,
    seats: Vector[TableSeat]
)