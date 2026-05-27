package riichinexus.microservices.tournament.appeal.objects.apiTypes

import riichinexus.domain.model.*
import riichinexus.microservices.tournament.appeal.domain.model.*
import riichinexus.microservices.tournament.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import upickle.default.*

final case class AdjudicateAppealRequest(
    operatorId: String,
    decision: String,
    verdict: String,
    tableResolution: Option[String] = None,
    note: Option[String] = None
):
  def operator: PlayerId =
    PlayerId(operatorId)

  def decisionType: AppealDecisionType =
    AppealDecisionType.valueOf(decision)

  def resolution: Option[AppealTableResolution] =
    tableResolution.map(AppealTableResolution.valueOf)

object AdjudicateAppealRequest:
  given ReadWriter[AdjudicateAppealRequest] = macroRW

