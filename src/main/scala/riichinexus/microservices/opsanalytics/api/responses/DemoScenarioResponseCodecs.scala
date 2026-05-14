package riichinexus.microservices.opsanalytics.api.responses

import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.publicquery.api.responses.PublicQueryResponses.given
import upickle.default.*

object DemoScenarioResponseCodecs:
  given ReadWriter[DemoScenarioVariant] =
    readwriter[String].bimap[DemoScenarioVariant](
      _.toString,
      DemoScenarioVariant.valueOf
    )
  given ReadWriter[DemoScenarioActionCode] =
    readwriter[String].bimap[DemoScenarioActionCode](
      _.toString,
      DemoScenarioActionCode.valueOf
    )
  given ReadWriter[DemoScenarioDashboardSummary] = macroRW
  given ReadWriter[DemoScenarioAdvancedStatsSummary] = macroRW
  given ReadWriter[DemoScenarioPlayerView] = macroRW
  given ReadWriter[DemoScenarioClubView] = macroRW
  given ReadWriter[DemoScenarioTableSeatView] = macroRW
  given ReadWriter[DemoScenarioTableView] = macroRW
  given ReadWriter[DemoScenarioTournamentView] = macroRW
  given ReadWriter[DemoScenarioReadiness] = macroRW
  given ReadWriter[DemoScenarioApiRequest] = macroRW
  given ReadWriter[DemoScenarioWidgetMetric] = macroRW
  given ReadWriter[DemoScenarioWidgetCount] = macroRW
  given ReadWriter[DemoScenarioWidgets] = macroRW
  given ReadWriter[DemoScenarioActionSpec] = macroRW
  given ReadWriter[DemoScenarioActionResult] = macroRW
  given ReadWriter[DemoScenarioGuideStep] = macroRW
  given ReadWriter[DemoScenarioGuide] = macroRW
  given ReadWriter[DemoScenarioSnapshot] = macroRW
