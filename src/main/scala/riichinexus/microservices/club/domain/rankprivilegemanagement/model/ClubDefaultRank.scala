package riichinexus.microservices.club.domain.rankprivilegemanagement.model

enum ClubDefaultRank(
    val code: String,
    val label: String,
    val minimumContribution: Int
):
  case Rookie extends ClubDefaultRank("rookie", "见习雀士", 0)
  case Member extends ClubDefaultRank("member", "正式队员", 500)
  case Core extends ClubDefaultRank("core", "主力队员", 1500)
  case Ace extends ClubDefaultRank("ace", "王牌队员", 3000)
