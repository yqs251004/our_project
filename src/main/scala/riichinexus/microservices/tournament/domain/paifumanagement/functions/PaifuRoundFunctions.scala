package riichinexus.microservices.tournament.domain.paifumanagement.functions

import riichinexus.domain.model.*
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
import riichinexus.domain.model.PlayerId
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
