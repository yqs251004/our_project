package riichinexus.microservices.tournament.domain.model

import riichinexus.domain.model.{ClubId, PlayerId}

final case class TournamentSettlementEntry(
    playerId: PlayerId,
    rank: Int,
    awardAmount: Long,
    baseAwardAmount: Long,
    adjustmentAmount: Long = 0L,
    deductionAmount: Long = 0L,
    clubId: Option[ClubId] = None,
    clubShareAmount: Long = 0L,
    playerRetainedAmount: Long = 0L,
    finalPoints: Int,
    champion: Boolean = false
) derives CanEqual
