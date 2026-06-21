package riichinexus.microservices.tournament.objects.competition.apiTypes

import upickle.default.{ReadWriter, macroRW}

/** 赛事详情页展示的白名单汇总。
  *
  * 它同时给出总条目数、俱乐部/玩家数量和对应 ID 列表，让后台无需展开完整白名单也能判断邀请范围。
  */
final case class TournamentWhitelistSummaryView(
    totalEntries: Int,
    clubCount: Int,
    playerCount: Int,
    clubIds: Vector[String],
    playerIds: Vector[String]
)

object TournamentWhitelistSummaryView:
  given ReadWriter[TournamentWhitelistSummaryView] = macroRW
