package riichinexus.microservices.club.objects

enum ClubDefaultRank(
    val code: String,
    val label: String,
    val minimumContribution: Int
) derives CanEqual:
  case Rookie extends ClubDefaultRank("rookie", "萌新", 0)
  case Member extends ClubDefaultRank("member", "同伴", 500)
  case Core extends ClubDefaultRank("core", "主力", 1500)
  case Ace extends ClubDefaultRank("ace", "王牌", 3000)

object ClubDefaultRank:
  val all: Vector[ClubDefaultRank] =
    Vector(Rookie, Member, Core, Ace)
