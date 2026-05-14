package riichinexus.microservices.tournament.api.responses

import riichinexus.infrastructure.json.JsonCodecs.given
import upickle.default.*

object TournamentResponseCodecs:
  given ReadWriter[TournamentStageDirectoryEntry] = macroRW
  given ReadWriter[TournamentParticipantClubView] = macroRW
  given ReadWriter[TournamentParticipantPlayerView] = macroRW
  given ReadWriter[TournamentWhitelistSummaryView] = macroRW
  given ReadWriter[TournamentLineupSubmissionView] = macroRW
  given ReadWriter[TournamentOperationsStageView] = macroRW
  given ReadWriter[TournamentDetailView] = macroRW
  given ReadWriter[TournamentMutationView] = macroRW
