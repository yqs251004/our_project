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
import riichinexus.microservices.tournament.objects.paifumanagement.PaifuRound

object PaifuRoundFunctions:
  def validate(round: PaifuRound): Unit =
    require(round.players.nonEmpty, "Round must contain players")
    val playerIds = round.players.map(_.playerId).toSet
    require(playerIds.size == round.players.size, "Round players must be unique")
    require(round.players.map(_.seat).distinct.size == round.players.size, "Round player seats must be unique")
    round.players.foreach { player =>
      PaifuTileFunctions.validateAll(player.initialHand.tiles, s"Initial hand for player ${player.playerId.value}")
      player.track.events.foreach(PaifuActionFunctions.validate)
      require(
        player.track.events.forall(_.actor.contains(player.playerId)),
        s"Player track for ${player.playerId.value} must contain that player's events only"
      )
    }
    require(round.timeline.events.nonEmpty, "Round timeline must contain at least one event")
    round.timeline.events.foreach(PaifuActionFunctions.validate)
    AgariResultFunctions.validate(round.result)
    require(
      round.timeline.events.map(_.sequenceNo).distinct.size == round.timeline.events.size,
      "Round timeline events must have unique sequence numbers"
    )
    require(
      round.timeline.events.map(_.sequenceNo) == round.timeline.events.map(_.sequenceNo).sorted,
      "Round timeline events must be sorted by sequence number"
    )
    require(
      round.timeline.events.forall(_.actor.forall(playerIds.contains)),
      "Round timeline events must reference seated players only"
    )
    require(
      round.timeline.events.forall(_.fromPlayer.forall(playerIds.contains)),
      "Round timeline claimed discards must reference seated players only"
    )
    val sequenceNumbers = round.timeline.events.map(_.sequenceNo).toSet
    require(
      round.timeline.events.forall(action => action.targetSequenceNo.forall(sequenceNumbers.contains)),
      "Round timeline target sequence numbers must reference existing events"
    )
    require(
      round.players.forall(player => trackMatchesTimeline(player.playerId, player.track.events.map(_.sequenceNo), round)),
      "Round player tracks must match the timeline events for each player"
    )
    require(
      round.result.scoreChanges.map(_.playerId).toSet == playerIds,
      "Round score changes must cover the same players as the round player list"
    )
    require(
      round.result.winner.forall(playerIds.contains),
      "Round winner must be seated in the round player list"
    )
    require(
      round.result.target.forall(playerIds.contains),
      "Round target must be seated in the round player list"
    )
    require(
      round.result.tenpaiPlayerIds.toVector.flatten.forall(playerIds.contains),
      "Round tenpai player ids must reference seated players only"
    )

  private def trackMatchesTimeline(playerId: PlayerId, trackSequenceNumbers: Vector[Int], round: PaifuRound): Boolean =
    val timelineSequenceNumbers = round.timeline.events.collect {
      case event if event.actor.contains(playerId) => event.sequenceNo
    }
    trackSequenceNumbers == timelineSequenceNumbers
