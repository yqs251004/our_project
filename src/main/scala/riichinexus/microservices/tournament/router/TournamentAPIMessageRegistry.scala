package riichinexus.microservices.tournament.router
import riichinexus.system.api.RegisteredAPIMessage

import riichinexus.system.json.JsonCodecs.given
import riichinexus.microservices.tournament.api.{GetPublicTournamentAPIMessage, ListPublicSchedulesAPIMessage, ListPublicTournamentsAPIMessage, TournamentAssignAdminAPIMessage, TournamentCreateAPIMessage, TournamentGetAPIMessage, TournamentInviteClubAPIMessage, TournamentListAPIMessage, TournamentPaifuGetAPIMessage, TournamentPaifuListAPIMessage, TournamentPublishAPIMessage, TournamentRecordGetAPIMessage, TournamentRecordListAPIMessage, TournamentRegisterPlayerAPIMessage, TournamentRemoveClubParticipationAPIMessage, TournamentRevokeAdminAPIMessage, TournamentSettleAPIMessage, TournamentSettlementFinalizeAPIMessage, TournamentSettlementGetAPIMessage, TournamentSettlementListAPIMessage, TournamentStageAdvanceAPIMessage, TournamentStageAdvancementPreviewAPIMessage, TournamentStageCompleteAPIMessage, TournamentStageConfigureRulesAPIMessage, TournamentStageCreateAPIMessage, TournamentStageDirectoryAPIMessage, TournamentStageKnockoutBracketAPIMessage, TournamentStageScheduleTablesAPIMessage, TournamentStageStandingsAPIMessage, TournamentStageSubmitLineupAPIMessage, TournamentStageTablesAPIMessage, TournamentStartAPIMessage, TournamentTableFinalizeArchiveAPIMessage, TournamentTableGetAPIMessage, TournamentTableListAPIMessage, TournamentTableResetAPIMessage, TournamentTableStartAPIMessage, TournamentTableUpdateOwnReadyAPIMessage, TournamentTableUpdateSeatStateAPIMessage, TournamentTableUploadPaifuAPIMessage, TournamentWhitelistListAPIMessage, TournamentWhitelistPlayerAPIMessage}
import riichinexus.microservices.tournament.objects.stage.rules.knockout.KnockoutBracketSnapshot
import riichinexus.microservices.tournament.objects.paifu.Paifu
import riichinexus.microservices.tournament.objects.stage.rules.progression.StageAdvancementSnapshot
import riichinexus.microservices.tournament.objects.stage.ranking.StageRankingSnapshot
import riichinexus.microservices.tournament.objects.competition.TournamentWhitelistEntry
import riichinexus.microservices.tournament.objects.paifu.apiTypes.PaifuSummary
import riichinexus.microservices.tournament.objects.matchrecord.apiTypes.TournamentMatchRecordView
import riichinexus.microservices.tournament.objects.finalization.apiTypes.TournamentSettlementView
import riichinexus.microservices.tournament.objects.stage.table.apiTypes.TournamentTableView
import riichinexus.microservices.tournament.objects.competition.apiTypes.{PublicScheduleView, PublicTournamentDetailView, PublicTournamentSummaryView, TournamentDetailView, TournamentMutationView, TournamentSummaryView}
import riichinexus.microservices.tournament.objects.stage.apiTypes.TournamentStageDirectoryEntry

import riichinexus.system.objects.PagedResponse

object TournamentAPIMessageRegistry:

  val apiMessages: Vector[RegisteredAPIMessage] =
    Vector(
      RegisteredAPIMessage.api[TournamentListAPIMessage, PagedResponse[TournamentSummaryView]],
      RegisteredAPIMessage.api[TournamentGetAPIMessage, TournamentDetailView],
      RegisteredAPIMessage.api[ListPublicSchedulesAPIMessage, PagedResponse[PublicScheduleView]],
      RegisteredAPIMessage.api[ListPublicTournamentsAPIMessage, PagedResponse[PublicTournamentSummaryView]],
      RegisteredAPIMessage.api[GetPublicTournamentAPIMessage, PublicTournamentDetailView],
      RegisteredAPIMessage.api[TournamentStageDirectoryAPIMessage, Vector[TournamentStageDirectoryEntry]],
      RegisteredAPIMessage.api[TournamentWhitelistListAPIMessage, PagedResponse[TournamentWhitelistEntry]],
      RegisteredAPIMessage.api[TournamentSettlementListAPIMessage, PagedResponse[TournamentSettlementView]],
      RegisteredAPIMessage.api[TournamentSettlementGetAPIMessage, TournamentSettlementView],
      RegisteredAPIMessage.created[TournamentCreateAPIMessage, TournamentSummaryView],
      RegisteredAPIMessage.api[TournamentPublishAPIMessage, TournamentMutationView],
      RegisteredAPIMessage.api[TournamentStartAPIMessage, TournamentSummaryView],
      RegisteredAPIMessage.api[TournamentSettleAPIMessage, TournamentSettlementView],
      RegisteredAPIMessage.api[TournamentSettlementFinalizeAPIMessage, TournamentSettlementView],
      RegisteredAPIMessage.api[TournamentRegisterPlayerAPIMessage, TournamentSummaryView],
      RegisteredAPIMessage.api[TournamentInviteClubAPIMessage, TournamentMutationView],
      RegisteredAPIMessage.api[TournamentRemoveClubParticipationAPIMessage, TournamentMutationView],
      RegisteredAPIMessage.api[TournamentWhitelistPlayerAPIMessage, TournamentSummaryView],
      RegisteredAPIMessage.api[TournamentAssignAdminAPIMessage, TournamentSummaryView],
      RegisteredAPIMessage.api[TournamentRevokeAdminAPIMessage, TournamentSummaryView],
      RegisteredAPIMessage.api[TournamentStageCreateAPIMessage, TournamentSummaryView],
      RegisteredAPIMessage.api[TournamentStageConfigureRulesAPIMessage, TournamentSummaryView],
      RegisteredAPIMessage.api[TournamentStageSubmitLineupAPIMessage, TournamentMutationView],
      RegisteredAPIMessage.api[TournamentStageScheduleTablesAPIMessage, TournamentMutationView],
      RegisteredAPIMessage.api[TournamentStageStandingsAPIMessage, StageRankingSnapshot],
      RegisteredAPIMessage.api[TournamentStageTablesAPIMessage, PagedResponse[TournamentTableView]],
      RegisteredAPIMessage.api[TournamentStageAdvancementPreviewAPIMessage, StageAdvancementSnapshot],
      RegisteredAPIMessage.api[TournamentStageKnockoutBracketAPIMessage, KnockoutBracketSnapshot],
      RegisteredAPIMessage.api[TournamentStageAdvanceAPIMessage, Vector[TournamentTableView]],
      RegisteredAPIMessage.api[TournamentStageCompleteAPIMessage, StageAdvancementSnapshot],
      RegisteredAPIMessage.api[TournamentTableListAPIMessage, PagedResponse[TournamentTableView]],
      RegisteredAPIMessage.api[TournamentTableGetAPIMessage, TournamentTableView],
      RegisteredAPIMessage.api[TournamentTableUpdateSeatStateAPIMessage, TournamentTableView],
      RegisteredAPIMessage.api[TournamentTableUpdateOwnReadyAPIMessage, TournamentTableView],
      RegisteredAPIMessage.api[TournamentTableStartAPIMessage, TournamentTableView],
      RegisteredAPIMessage.api[TournamentTableUploadPaifuAPIMessage, TournamentTableView],
      RegisteredAPIMessage.api[TournamentTableFinalizeArchiveAPIMessage, TournamentTableView],
      RegisteredAPIMessage.api[TournamentTableResetAPIMessage, TournamentTableView],
      RegisteredAPIMessage.api[TournamentRecordListAPIMessage, PagedResponse[TournamentMatchRecordView]],
      RegisteredAPIMessage.api[TournamentRecordGetAPIMessage, TournamentMatchRecordView],
      RegisteredAPIMessage.api[TournamentPaifuListAPIMessage, PagedResponse[PaifuSummary]],
      RegisteredAPIMessage.api[TournamentPaifuGetAPIMessage, Paifu]
    )
