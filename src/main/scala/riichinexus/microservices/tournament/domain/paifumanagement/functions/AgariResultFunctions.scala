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
import riichinexus.microservices.tournament.objects.paifumanagement.{AgariResult, AgariWinResult, MahjongYakuKind, Yaku}
import riichinexus.microservices.tournament.objects.paifumanagement.HandOutcome

private[tournament] object AgariResultFunctions:
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
    val wins = effectiveWins(result)
    result.outcome match
      case HandOutcome.Ron =>
        require(result.winner.nonEmpty, "Ron result must include a winner")
        require(result.target.nonEmpty, "Ron result must include a target")
        require(wins.nonEmpty, "Ron result must include at least one win")
        wins.foreach { win =>
          require(win.target.nonEmpty, "Ron win result must include a target")
          validateWinningDetails(win.han.orElse(result.han), win.fu.orElse(result.fu), effectiveYaku(win, result))
        }
      case HandOutcome.Tsumo =>
        require(result.winner.nonEmpty, "Tsumo result must include a winner")
        require(result.target.isEmpty, "Tsumo result must not include a discard target")
        require(wins.nonEmpty, "Tsumo result must include at least one win")
        wins.foreach { win =>
          require(win.target.isEmpty, "Tsumo win result must not include a discard target")
          val yaku = effectiveYaku(win, result)
          validateWinningDetails(win.han.orElse(result.han), win.fu.orElse(result.fu), yaku, allowMissingFu = hasNagashiMangan(yaku))
        }
      case HandOutcome.ExhaustiveDraw | HandOutcome.AbortiveDraw =>
        require(result.winner.isEmpty, "Drawn hands cannot include a winner")
        require(result.target.isEmpty, "Drawn hands cannot include a target")
        require(result.han.isEmpty && result.fu.isEmpty, "Drawn hands cannot include han/fu")
        require(result.yaku.isEmpty, "Drawn hands cannot include yaku")

  private def effectiveWins(result: AgariResult): Vector[AgariWinResult] =
    if result.wins.nonEmpty then result.wins
    else
      result.winner.toVector.map { winner =>
        AgariWinResult(
          winner = winner,
          target = result.target,
          han = result.han,
          fu = result.fu,
          yaku = result.yaku,
          points = result.points,
          doraIndicators = result.doraIndicators,
          uraDoraIndicators = result.uraDoraIndicators,
          uraDoraVisible = result.uraDoraVisible
        )
      }

  private def effectiveYaku(win: AgariWinResult, result: AgariResult): Vector[Yaku] =
    if win.yaku.nonEmpty then win.yaku else result.yaku

  private def validateWinningDetails(
      han: Option[Int],
      fu: Option[Int],
      yaku: Vector[Yaku],
      allowMissingFu: Boolean = false
  ): Unit =
    require(han.nonEmpty && (fu.nonEmpty || allowMissingFu), "Winning hands must include han and fu")
    require(yaku.nonEmpty, "Winning hands must include at least one yaku")

  private def hasNagashiMangan(yaku: Vector[Yaku]): Boolean =
    yaku.exists(_.kind == MahjongYakuKind.NagashiMangan)
