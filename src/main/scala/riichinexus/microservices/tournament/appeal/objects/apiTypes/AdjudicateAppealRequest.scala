package riichinexus.microservices.tournament.appeal.objects.apiTypes

import riichinexus.domain.model.*
import riichinexus.microservices.tournament.appeal.domain.model.{
  AppealDecisionType as DomainAppealDecisionType,
  AppealTableResolution as DomainAppealTableResolution
}
import riichinexus.infrastructure.json.JsonCodecs.given
import upickle.default.*

final case class AdjudicateAppealRequest(
    operatorId: String,
    decision: AppealDecisionType,
    verdict: String,
    tableResolution: Option[AppealTableResolution] = None,
    note: Option[String] = None
):
  def operator: PlayerId =
    PlayerId(operatorId)

  def decisionType: DomainAppealDecisionType =
    decision.toDomain

  def resolution: Option[DomainAppealTableResolution] =
    tableResolution.map(_.toDomain)

object AdjudicateAppealRequest:
  given ReadWriter[AdjudicateAppealRequest] = macroRW
