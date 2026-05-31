package riichinexus.microservices.tournament.objects.tournamentmanagement.apiTypes

import riichinexus.domain.model.{ClubId, PlayerId}
import riichinexus.microservices.tournament.objects.tournamentmanagement.TournamentParticipantKind
import riichinexus.infrastructure.json.JsonCodecs.given
import upickle.default.*

final case class TournamentWhitelistQuery(
    participantKind: Option[TournamentParticipantKind] = None,
    playerId: Option[PlayerId] = None,
    clubId: Option[ClubId] = None,
    limit: Option[Int] = None,
    offset: Option[Int] = None
) derives ReadWriter
