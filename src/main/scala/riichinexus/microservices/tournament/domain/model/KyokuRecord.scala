package riichinexus.microservices.tournament.domain.model

import java.time.Instant

import riichinexus.domain.model.*


final case class KyokuRecord(
    descriptor: KyokuDescriptor,
    initialHands: Map[PlayerId, Vector[String]],
    actions: Vector[PaifuAction],
    result: AgariResult
) derives CanEqual:
  require(initialHands.nonEmpty, "Round must contain initial hands")
  require(actions.nonEmpty, "Round must contain at least one action")
  require(
    actions.map(_.sequenceNo).distinct.size == actions.size,
    "Round actions must have unique sequence numbers"
  )
  require(
    actions.map(_.sequenceNo) == actions.map(_.sequenceNo).sorted,
    "Round actions must be sorted by sequence number"
  )
  require(
    actions.forall(_.actor.forall(initialHands.contains)),
    "Round actions must reference seated players only"
  )
  require(
    result.scoreChanges.map(_.playerId).toSet == initialHands.keySet,
    "Round score changes must cover the same players as the initial hand map"
  )
  require(
    result.winner.forall(initialHands.contains),
    "Round winner must be seated in the initial hand map"
  )
  require(
    result.target.forall(initialHands.contains),
    "Round target must be seated in the initial hand map"
  )
  require(
    result.tenpaiPlayerIds.toVector.flatten.forall(initialHands.contains),
    "Round tenpai player ids must reference seated players only"
  )

