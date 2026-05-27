package riichinexus.microservices.tournament.appeal.objects.apiTypes

import riichinexus.domain.model.*
import riichinexus.microservices.tournament.appeal.domain.model.*
import riichinexus.microservices.tournament.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import upickle.default.*

final case class AppealDecisionLogView(
    operatorId: String,
    decision: String,
    decidedAt: String,
    note: Option[String]
) derives CanEqual

object AppealDecisionLogView:
  def fromDomain(log: AppealDecisionLog): AppealDecisionLogView =
    AppealDecisionLogView(
      operatorId = log.operatorId.value,
      decision = log.decision,
      decidedAt = log.decidedAt.toString,
      note = log.note
    )

  given ReadWriter[AppealDecisionLogView] = macroRW

