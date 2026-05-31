package riichinexus.microservices.tournament.appeal.objects

import java.time.Instant

import riichinexus.domain.model.PlayerId

final case class AppealDecisionLog(
    operatorId: PlayerId,
    decision: String,
    decidedAt: Instant,
    note: Option[String] = None
) derives CanEqual
