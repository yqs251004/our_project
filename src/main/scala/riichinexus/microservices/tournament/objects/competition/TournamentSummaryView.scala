package riichinexus.microservices.tournament.objects.competition

import upickle.default.{ReadWriter, macroRW}

import riichinexus.microservices.tournament.objects.competition.TournamentStatus
import riichinexus.microservices.tournament.objects.stage.TournamentStageSummaryView
import riichinexus.system.json.TournamentJsonCodecs.given

/** 运营后台赛事列表使用的摘要视图。
  *
  * 它保留参赛俱乐部、参赛玩家、管理员、白名单数量和阶段摘要，让后台列表可以直接展示赛事规模与管理入口。
  */
final case class TournamentSummaryView(
    tournamentId: String,
    name: String,
    organizer: String,
    startsAt: String,
    endsAt: String,
    status: TournamentStatus,
    participatingClubIds: Vector[String],
    participatingPlayerIds: Vector[String],
    adminIds: Vector[String],
    whitelistCount: Int,
    stages: Vector[TournamentStageSummaryView]
)

object TournamentSummaryView:
  given ReadWriter[TournamentSummaryView] = macroRW
