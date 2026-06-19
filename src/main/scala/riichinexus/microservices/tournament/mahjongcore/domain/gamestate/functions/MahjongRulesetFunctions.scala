package riichinexus.microservices.tournament.mahjongcore.domain.gamestate.functions

import riichinexus.microservices.tournament.mahjongcore.objects.gamestate.MahjongRuleset

private[mahjongcore] object MahjongRulesetFunctions:
  def normalizedAkaDoraCount(ruleset: MahjongRuleset): Int =
    if !ruleset.akaDora then 0 else math.max(0, math.min(ruleset.akaDoraCount, 3))

  def normalizedMinHan(ruleset: MahjongRuleset): Int =
    math.max(1, ruleset.minHan)
