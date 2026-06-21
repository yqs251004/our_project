package riichinexus.microservices.tournament.objects.stage.rules.progression

/** 阶段完成后选择晋级名单的规则类型。
  *
  * 不同类型分别表示按瑞士轮排名截断、淘汰赛胜者、分数阈值或自定义模板生成后续阶段参与者。
  */
enum AdvancementRuleType:
  case SwissCut
  case KnockoutElimination
  case ScoreThreshold
  case Custom

object AdvancementRuleType:
  def toString(ruleType: AdvancementRuleType): String =
    ruleType.toString

  def fromString(value: String): AdvancementRuleType =
    AdvancementRuleType.valueOf(value)
