package riichinexus.microservices.tournament.objects

import riichinexus.microservices.tournament.domain.model.{TournamentParticipantKind as DomainTournamentParticipantKind}
import upickle.default.*

enum TournamentParticipantKind derives CanEqual:
  case Club
  case Player

  def toDomain: DomainTournamentParticipantKind =
    DomainTournamentParticipantKind.valueOf(toString)

object TournamentParticipantKind:
  given ReadWriter[TournamentParticipantKind] =
    readwriter[String].bimap(_.toString, TournamentParticipantKind.valueOf)

  def fromDomain(kind: DomainTournamentParticipantKind): TournamentParticipantKind =
    TournamentParticipantKind.valueOf(kind.toString)
