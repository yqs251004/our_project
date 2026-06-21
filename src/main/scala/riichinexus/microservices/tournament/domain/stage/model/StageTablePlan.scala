package riichinexus.microservices.tournament.domain.stage.model


import riichinexus.microservices.tournament.objects.stage.table.TableSeat

import riichinexus.system.json.JsonCodecs.given

/** 阶段调度生成但尚未落库成正式牌桌的桌次计划。
  *
  * 计划记录轮次、桌号和四个座位安排，等待运营确认或批量创建牌桌时使用。
  */
final case class StageTablePlan(
    roundNumber: Int,
    tableNo: Int,
    seats: Vector[TableSeat]
)
