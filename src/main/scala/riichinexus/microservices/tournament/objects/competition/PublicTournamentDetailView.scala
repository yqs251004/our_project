package riichinexus.microservices.tournament.objects.competition

import riichinexus.microservices.tournament.objects.competition.TournamentStatus
import riichinexus.microservices.tournament.objects.stage.PublicTournamentStageView
import riichinexus.system.json.TournamentJsonCodecs.given
import upickle.default.{ReadWriter, macroRW}

/** 公开赛事详情页使用的安全展示视图。
  *
  * 它展示赛事基本信息、公开参赛主体、白名单数量和公开阶段信息，不包含后台管理员列表或阶段操作字段。
  */
final case class PublicTournamentDetailView(
    tournamentId: String,
    name: String,
    organizer: String,
    status: TournamentStatus,
    startsAt: String,
    endsAt: String,
    clubIds: Vector[String],
    playerIds: Vector[String],
    whitelistCount: Int,
    stages: Vector[PublicTournamentStageView]
)

object PublicTournamentDetailView:
  given ReadWriter[PublicTournamentDetailView] = macroRW
