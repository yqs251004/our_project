package riichinexus.microservices.tournament.domain.stage.functions.scheduling

import riichinexus.microservices.tournament.domain.stage.model.StageTablePlan


/** StageTablePlanFunctions 提供阶段牌桌Plan相关的领域计算、校验和转换函数。 */


private[tournament] object StageTablePlanFunctions:
  def validate(plan: StageTablePlan): Unit =
    require(plan.roundNumber >= 1, "Stage table plan round number must be positive")
    require(plan.seats.size == 4, "Stage table plan must contain four seats")
