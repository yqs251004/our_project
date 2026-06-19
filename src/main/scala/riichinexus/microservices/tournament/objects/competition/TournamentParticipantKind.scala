package riichinexus.microservices.tournament.objects.competition

/** TournamentParticipantKind 枚举赛事参赛方类型 可使用的公开取值。 */

enum TournamentParticipantKind:
  case Club
  case Player

object TournamentParticipantKind:
  def toString(kind: TournamentParticipantKind): String =
    kind.toString

  def fromString(value: String): TournamentParticipantKind =
    TournamentParticipantKind.valueOf(value)
