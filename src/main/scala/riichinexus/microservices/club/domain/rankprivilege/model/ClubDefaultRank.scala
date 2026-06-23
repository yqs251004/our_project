package riichinexus.microservices.club.domain.rankprivilege.model

/** 系统内置的俱乐部默认等级模板。
  *
  * 每个等级给出稳定代码、展示名称和最低贡献门槛，用于新俱乐部初始化等级树。
  */
enum ClubDefaultRank(
    val code: String,
    val label: String,
    val minimumContribution: Int
):
  case Rookie extends ClubDefaultRank("rookie", "见习雀士", 0)
  case Member extends ClubDefaultRank("member", "正式队员", 500)
  case Core extends ClubDefaultRank("core", "主力队员", 1500)
  case Ace extends ClubDefaultRank("ace", "王牌队员", 3000)
