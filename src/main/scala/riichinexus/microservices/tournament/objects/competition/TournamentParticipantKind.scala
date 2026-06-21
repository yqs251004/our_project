package riichinexus.microservices.tournament.objects.competition

/** 赛事白名单和参赛名单中允许出现的主体类型。
  *
  * 俱乐部参赛通常需要阵容提交，个人参赛则直接绑定玩家档案；二者在邀请和排桌时走不同逻辑。
  */
enum TournamentParticipantKind:
  case Club
  case Player

object TournamentParticipantKind:
  def toString(kind: TournamentParticipantKind): String =
    kind.toString

  def fromString(value: String): TournamentParticipantKind =
    TournamentParticipantKind.valueOf(value)
