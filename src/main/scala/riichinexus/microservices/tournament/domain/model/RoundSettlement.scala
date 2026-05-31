package riichinexus.microservices.tournament.domain.model

import java.time.Instant

import riichinexus.domain.model.*


final case class RoundSettlement(
    riichiSticksDelta: Int = 0,
    honbaPayment: Int = 0,
    notes: Vector[String] = Vector.empty
) derives CanEqual:
  require(riichiSticksDelta >= 0, "Riichi sticks delta must be non-negative")
  require(honbaPayment >= 0, "Honba payment must be non-negative")
  require(riichiSticksDelta % 1000 == 0, "Riichi sticks delta must be a multiple of 1000")
  require(honbaPayment % 100 == 0, "Honba payment must be a multiple of 100")
  require(
    notes.forall(_.trim.nonEmpty),
    "Round settlement notes cannot contain blank entries"
  )

