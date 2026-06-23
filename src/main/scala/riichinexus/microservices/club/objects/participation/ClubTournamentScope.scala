package riichinexus.microservices.club.objects.participation

/** 俱乐部赛事列表的时间范围筛选。
  *
  * 成员中心和俱乐部详情页用它在近期、进行中和全部赛事之间切换，后端据此决定查询窗口。
  */
enum ClubTournamentScope:
  case Recent
  case Active
  case All

object ClubTournamentScope:
  def toString(scope: ClubTournamentScope): String =
    scope match
      case Recent => "recent"
      case Active => "active"
      case All    => "all"

  def fromString(value: String): ClubTournamentScope =
    value match
      case "recent" => Recent
      case "active" => Active
      case "all"    => All
      case other    => throw IllegalArgumentException(s"Unknown club tournament scope $other")
