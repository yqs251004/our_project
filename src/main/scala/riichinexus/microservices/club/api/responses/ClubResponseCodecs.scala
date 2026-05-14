package riichinexus.microservices.club.api.responses

import riichinexus.infrastructure.json.JsonCodecs.given
import upickle.default.*

object ClubResponseCodecs:
  given ReadWriter[ClubMembershipApplicantView] = macroRW
  given ReadWriter[ClubMembershipApplicationView] = macroRW
  given ReadWriter[ClubTournamentParticipationStatus] =
    readwriter[String].bimap[ClubTournamentParticipationStatus](_.toString, ClubTournamentParticipationStatus.valueOf)
  given ReadWriter[ClubTournamentParticipationView] = macroRW
