package riichinexus.microservices.publicquery.api.responses

import riichinexus.infrastructure.json.JsonCodecs.given
import upickle.default.*

object PublicQueryResponseCodecs:
  given ReadWriter[PublicScheduleView] = macroRW
  given ReadWriter[PublicClubDirectoryEntry] = macroRW
  given ReadWriter[PlayerLeaderboardEntry] = macroRW
  given ReadWriter[ClubLeaderboardEntry] = macroRW
  given ReadWriter[ClubApplicationPolicyView] = macroRW
  given ReadWriter[PublicClubLineupMemberView] = macroRW
  given ReadWriter[PublicClubRecentMatchSeatView] = macroRW
  given ReadWriter[PublicClubRecentMatchView] = macroRW
  given ReadWriter[PublicClubDetailView] = macroRW
  given ReadWriter[PublicTournamentSummaryView] = macroRW
  given ReadWriter[PublicTournamentStageView] = macroRW
  given ReadWriter[PublicTournamentDetailView] = macroRW
