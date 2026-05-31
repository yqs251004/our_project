package riichinexus.microservices.tournament.objects.rulesmanagement.apiTypes

import riichinexus.domain.model.*
import riichinexus.microservices.tournament.domain.lineupmanagement.model.*
import riichinexus.microservices.tournament.domain.recordmanagement.model.*
import riichinexus.microservices.tournament.domain.settlementmanagement.model.*
import riichinexus.microservices.tournament.domain.tablemanagement.model.*
import riichinexus.microservices.tournament.domain.tournamentmanagement.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.tournament.objects.rulesmanagement.stageprogression.AdvancementRuleType
import riichinexus.microservices.tournament.objects.tournamentmanagement.TournamentFormat
import upickle.default.*

final case class ConfigureStageRulesRequest(
    operatorId: String,
    format: Option[TournamentFormat] = None,
    roundCount: Option[Int] = None,
    advancementRuleType: Option[String] = None,
    cutSize: Option[Int] = None,
    thresholdScore: Option[Int] = None,
    targetTableCount: Option[Int] = None,
    schedulingPoolSize: Option[Int] = None,
    ruleTemplateKey: Option[String] = None,
    pairingMethod: Option[String] = None,
    carryOverPoints: Option[Boolean] = None,
    maxRounds: Option[Int] = None,
    bracketSize: Option[Int] = None,
    thirdPlaceMatch: Option[Boolean] = None,
    repechageEnabled: Option[Boolean] = None,
    seedingPolicy: Option[String] = None,
    note: Option[String] = None
)

object ConfigureStageRulesRequest:
  given ReadWriter[ConfigureStageRulesRequest] = macroRW
