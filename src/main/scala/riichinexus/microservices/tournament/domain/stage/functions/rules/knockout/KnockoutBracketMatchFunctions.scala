package riichinexus.microservices.tournament.domain.stage.functions.rules.knockout


import riichinexus.microservices.tournament.objects.rulesmanagement.knockout.KnockoutBracketMatch

/** KnockoutBracketMatchFunctions 提供KnockoutBracket对局相关的领域计算、校验和转换函数。 */

private[tournament] object KnockoutBracketMatchFunctions:
  def validate(matchNode: KnockoutBracketMatch): Unit =
    require(matchNode.slots.size == 4, "Riichi knockout matches must contain exactly four slots")
    require(matchNode.advancementCount >= 0 && matchNode.advancementCount <= 4, "Advancement count must be between 0 and 4")
