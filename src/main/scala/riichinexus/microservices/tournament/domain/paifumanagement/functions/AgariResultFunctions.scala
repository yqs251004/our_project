package riichinexus.microservices.tournament.domain.paifumanagement.functions

import riichinexus.microservices.player.domain.functions.PlayerIdGenerator
import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.microservices.club.domain.functions.ClubIdGenerator
import riichinexus.microservices.club.objects.clubmanagement.ClubId
import riichinexus.microservices.club.objects.membershipmanagement.MembershipApplicationId
import riichinexus.microservices.tournament.domain.functions.TournamentIdGenerator
import riichinexus.microservices.tournament.objects.lineupmanagement.LineupSubmissionId
import riichinexus.microservices.tournament.objects.paifumanagement.PaifuId
import riichinexus.microservices.tournament.objects.recordmanagement.MatchRecordId
import riichinexus.microservices.tournament.objects.settlementmanagement.SettlementSnapshotId
import riichinexus.microservices.tournament.objects.tablemanagement.TableId
import riichinexus.microservices.tournament.objects.tournamentmanagement.{TournamentId, TournamentStageId}
import riichinexus.microservices.tournament.appeal.domain.functions.AppealIdGenerator
import riichinexus.microservices.tournament.appeal.objects.ticketmanagement.AppealTicketId
import riichinexus.microservices.auth.domain.functions.AuthIdGenerator
import riichinexus.microservices.auth.objects.sessionmanagement.GuestSessionId
import riichinexus.microservices.audit.domain.functions.AuditIdGenerator
import riichinexus.microservices.audit.domain.auditevent.AuditEventId
import riichinexus.microservices.opsanalytics.domain.functions.OpsAnalyticsIdGenerator
import riichinexus.microservices.opsanalytics.objects.advancedstats.AdvancedStatsRecomputeTaskId
import riichinexus.microservices.tournament.domain.lineupmanagement.model.*
import riichinexus.microservices.tournament.domain.recordmanagement.model.*
import riichinexus.microservices.tournament.domain.settlementmanagement.model.*
import riichinexus.microservices.tournament.domain.tablemanagement.model.*
import riichinexus.microservices.tournament.domain.tournamentmanagement.model.*
import riichinexus.microservices.tournament.domain.lineupmanagement.functions.*
import riichinexus.microservices.tournament.domain.paifumanagement.functions.*
import riichinexus.microservices.tournament.domain.recordmanagement.functions.*
import riichinexus.microservices.tournament.domain.rulesmanagement.functions.*
import riichinexus.microservices.tournament.domain.rulesmanagement.functions.knockout.*
import riichinexus.microservices.tournament.domain.rulesmanagement.functions.ranking.*
import riichinexus.microservices.tournament.domain.rulesmanagement.functions.stageprogression.*
import riichinexus.microservices.tournament.domain.rulesmanagement.functions.swiss.*
import riichinexus.microservices.tournament.domain.settlementmanagement.functions.*
import riichinexus.microservices.tournament.domain.tablemanagement.functions.*
import riichinexus.microservices.tournament.domain.tournamentmanagement.functions.*
import riichinexus.microservices.tournament.objects.paifumanagement.AgariResult
import riichinexus.microservices.tournament.objects.paifumanagement.HandOutcome

object AgariResultFunctions:
  def validate(result: AgariResult): Unit =
    require(result.points >= 0, "Result points must be non-negative")
    require(result.scoreChanges.nonEmpty, "Result must include score changes")
    require(
      result.scoreChanges.map(_.playerId).distinct.size == result.scoreChanges.size,
      "Score changes cannot contain duplicate players"
    )
    result.doraIndicators.foreach { indicators =>
      require(indicators.size == 5, "Dora indicators must contain exactly five tiles when provided")
      PaifuTileFunctions.validateAll(indicators, "Dora indicators")
    }
    result.uraDoraIndicators.foreach { indicators =>
      require(indicators.size == 5, "Ura-dora indicators must contain exactly five tiles when provided")
      PaifuTileFunctions.validateAll(indicators, "Ura-dora indicators")
    }
    result.outcome match
      case HandOutcome.Ron =>
        require(result.winner.nonEmpty, "Ron result must include a winner")
        require(result.target.nonEmpty, "Ron result must include a target")
        require(result.han.nonEmpty && result.fu.nonEmpty, "Winning hands must include han and fu")
        require(result.yaku.nonEmpty, "Winning hands must include at least one yaku")
      case HandOutcome.Tsumo =>
        require(result.winner.nonEmpty, "Tsumo result must include a winner")
        require(result.target.isEmpty, "Tsumo result must not include a discard target")
        require(result.han.nonEmpty && result.fu.nonEmpty, "Winning hands must include han and fu")
        require(result.yaku.nonEmpty, "Winning hands must include at least one yaku")
      case HandOutcome.ExhaustiveDraw | HandOutcome.AbortiveDraw =>
        require(result.winner.isEmpty, "Drawn hands cannot include a winner")
        require(result.target.isEmpty, "Drawn hands cannot include a target")
        require(result.han.isEmpty && result.fu.isEmpty, "Drawn hands cannot include han/fu")
        require(result.yaku.isEmpty, "Drawn hands cannot include yaku")
