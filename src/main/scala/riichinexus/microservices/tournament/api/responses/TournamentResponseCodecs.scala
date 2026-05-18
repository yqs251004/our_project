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
  given ReadWriter[TournamentStageSummaryView] = macroRW
  given ReadWriter[TournamentSummaryView] = macroRW
  given ReadWriter[TournamentWhitelistEntryView] = macroRW
  given ReadWriter[TournamentTableSeatView] = macroRW
  given ReadWriter[TournamentTableView] = macroRW
  given ReadWriter[TournamentMatchRecordSeatResultView] = macroRW
  given ReadWriter[TournamentMatchRecordView] = macroRW
  given ReadWriter[TournamentPaifuFinalStandingView] = macroRW
  given ReadWriter[TournamentPaifuSummaryView] = macroRW
  given ReadWriter[TournamentSettlementAdjustmentView] = macroRW
  given ReadWriter[TournamentSettlementEntryView] = macroRW
  given ReadWriter[TournamentSettlementView] = macroRW
  given ReadWriter[TournamentMutationView] = macroRW
