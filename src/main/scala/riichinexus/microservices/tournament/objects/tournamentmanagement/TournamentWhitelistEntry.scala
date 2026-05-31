package riichinexus.microservices.tournament.objects.tournamentmanagement

import riichinexus.domain.model.{ClubId, PlayerId}

final case class TournamentWhitelistEntry(
    participantKind: TournamentParticipantKind,
    clubId: Option[ClubId] = None,
    playerId: Option[PlayerId] = None
) derives CanEqual
