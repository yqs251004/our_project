package riichinexus.microservices.tournament.objects.competition.apiTypes

import upickle.default.{ReadWriter, macroRW}

/** TournamentWhitelistSummaryView 表示赛事白名单摘要视图 的前端展示视图。 */

final case class TournamentWhitelistSummaryView(
    totalEntries: Int,
    clubCount: Int,
    playerCount: Int,
    clubIds: Vector[String],
    playerIds: Vector[String]
)

object TournamentWhitelistSummaryView:
  given ReadWriter[TournamentWhitelistSummaryView] = macroRW
