package riichinexus.microservices.tournament.objects.competition

import upickle.default.{ReadWriter, macroRW}

/** 赛事详情中展示的参赛俱乐部摘要。
  *
  * 当前只携带俱乐部 ID 和成员数量，足够后台展示俱乐部参赛规模并进入后续阵容选择流程。
  */
final case class TournamentParticipantClubView(
    clubId: String,
    memberCount: Int
)

object TournamentParticipantClubView:
  given ReadWriter[TournamentParticipantClubView] = macroRW
