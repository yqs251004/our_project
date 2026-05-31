package riichinexus.microservices.tournament.domain.model

import java.time.Instant
import java.util.NoSuchElementException

import riichinexus.domain.model.*
import riichinexus.microservices.tournament.objects.{SeatWind}

final case class MatchRecordSeatResult(
    playerId: PlayerId,
    seat: SeatWind,
    clubId: Option[ClubId] = None,
    finalPoints: Int,
    placement: Int,
    scoreDelta: Int,
    uma: Double = 0.0,
    oka: Double = 0.0
) derives CanEqual:
  require(placement >= 1 && placement <= 4, "Placement must be between 1 and 4")

