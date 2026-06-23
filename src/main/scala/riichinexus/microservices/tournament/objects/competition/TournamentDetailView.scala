package riichinexus.microservices.tournament.objects.competition

import upickle.default.{ReadWriter, macroRW}

import riichinexus.microservices.tournament.objects.competition.TournamentStatus
import riichinexus.microservices.tournament.objects.stage.TournamentOperationsStageView
import riichinexus.system.json.TournamentJsonCodecs.given

/** 赛事运营详情页使用的完整视图。
  *
  * 相比公开详情，它包含参赛俱乐部/玩家的管理信息、白名单摘要和运营阶段视图，用于赛事后台进行邀请、排桌和阶段推进。
  */
final case class TournamentDetailView(
    tournamentId: String,
    name: String,
    organizer: String,
    status: TournamentStatus,
    startsAt: String,
    endsAt: String,
    participatingClubs: Vector[TournamentParticipantClubView],
    participatingPlayers: Vector[TournamentParticipantPlayerView],
    whitelistSummary: TournamentWhitelistSummaryView,
    stages: Vector[TournamentOperationsStageView]
)

object TournamentDetailView:
  given ReadWriter[TournamentDetailView] = macroRW
