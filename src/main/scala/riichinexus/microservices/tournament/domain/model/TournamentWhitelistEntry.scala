package riichinexus.microservices.tournament.domain.model

import riichinexus.domain.model.{ClubId, PlayerId}
import riichinexus.microservices.tournament.objects.TournamentParticipantKind

final case class TournamentWhitelistEntry(
    participantKind: TournamentParticipantKind,
    clubId: Option[ClubId] = None,
    playerId: Option[PlayerId] = None
) derives CanEqual:
  require(
    participantKind match
      case TournamentParticipantKind.Club   => clubId.nonEmpty && playerId.isEmpty
      case TournamentParticipantKind.Player => playerId.nonEmpty && clubId.isEmpty,
    s"Invalid whitelist entry for $participantKind"
  )
