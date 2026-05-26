package riichinexus.microservices.club.objects

import upickle.default.*

final case class ClubTournamentParticipationView(
    clubId: String,
    tournamentId: String,
    name: String,
    status: String,
    clubParticipationStatus: String,
    stageName: Option[String],
    startsAt: String,
    endsAt: String,
    canViewDetail: Boolean,
    canSubmitLineup: Boolean,
    canDecline: Boolean
) derives ReadWriter
