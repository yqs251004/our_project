package riichinexus.microservices.tournament.objects.competition.apiTypes

import riichinexus.microservices.tournament.objects.stage.table.apiTypes.TournamentTableView
import upickle.default.{ReadWriter, macroRW}

/** 赛事写操作完成后返回给前端的刷新模型。
  *
  * 响应总是包含最新赛事详情，并在排桌等操作后附带新生成的牌桌视图，减少前端紧接着二次拉取。
  */
final case class TournamentMutationView(
    tournament: TournamentDetailView,
    scheduledTables: Vector[TournamentTableView] = Vector.empty
)

object TournamentMutationView:
  given ReadWriter[TournamentMutationView] = macroRW
