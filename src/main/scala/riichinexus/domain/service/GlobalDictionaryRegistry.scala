package riichinexus.domain.service

import scala.util.Try

import riichinexus.domain.model.*

object GlobalDictionaryRegistry:
  val RatingKFactorKey = "rating.elo.kfactor"
  val RatingPlacementWeightKey = "rating.elo.placementweight"
  val RatingScoreWeightKey = "rating.elo.scoreweight"
  val RatingUmaWeightKey = "rating.elo.umaweight"
  val ClubPowerEloWeightKey = "club.power.eloweight"
  val ClubPowerPointWeightKey = "club.power.pointweight"
  val ClubPowerBaseBonusKey = "club.power.basebonus"
  val SettlementPayoutRatiosKey = "settlement.defaultpayoutratios"
  val RankNormalizationPrefix = "rank.normalization."
  val TournamentRuleTemplatePrefix = "tournament.rule-template."
  private val ReservedRuntimePrefixes =
    Vector("rating.", "club.power.", "settlement.", RankNormalizationPrefix, TournamentRuleTemplatePrefix)

  val UnknownKeyPolicy =
    "Unknown keys outside reserved runtime namespaces remain allowed as free-form metadata. Unregistered keys inside reserved namespaces are rejected until they are added to the dictionary registry."

  final case class StageRuleTemplate(
      ruleType: Option[AdvancementRuleType] = None,
      cutSize: Option[Int] = None,
      thresholdScore: Option[Int] = None,
      targetTableCount: Option[Int] = None,
      schedulingPoolSize: Option[Int] = None,
      pairingMethod: Option[String] = None,
      carryOverPoints: Option[Boolean] = None,
      maxRounds: Option[Int] = None,
      bracketSize: Option[Int] = None,
      thirdPlaceMatch: Option[Boolean] = None,
      repechageEnabled: Option[Boolean] = None,
      seedingPolicy: Option[String] = None,
      note: Option[String] = None
  )

  sealed trait ResolvedKey:
    def schemaEntry: GlobalDictionarySchemaEntry
    def normalizedKey: String
    def validate(value: String): Unit

  private final case class KnownResolvedKey(
      schemaEntry: GlobalDictionarySchemaEntry,
      normalizedKey: String,
      validator: String => Unit
  ) extends ResolvedKey:
    override def validate(value: String): Unit = validator(value)

  private final case class MetadataResolvedKey(normalizedKey: String) extends ResolvedKey:
    override val schemaEntry: GlobalDictionarySchemaEntry = MetadataSchema
    override def validate(value: String): Unit = ()

  private val MetadataSchema = GlobalDictionarySchemaEntry(
    id = "metadata",
    keyPattern = "*",
    valueType = GlobalDictionaryValueType.Metadata,
    description = "Free-form metadata that is persisted but not consumed by runtime services.",
    validationHint = "Any non-empty string is accepted.",
    runtimeConsumers = Vector.empty,
    examples = Vector("ui.banner.message", "ops.runbook.url")
  )

  val schemaEntries: Vector[GlobalDictionarySchemaEntry] = Vector(
    GlobalDictionarySchemaEntry(
      id = "rating.elo.kFactor",
      keyPattern = "rating.elo.kFactor",
      valueType = GlobalDictionaryValueType.Integer,
      description = "ELO k-factor used by the live rating engine.",
      validationHint = "Positive integer.",
      runtimeConsumers = Vector("rating-service"),
      examples = Vector("rating.elo.kFactor")
    ),
    GlobalDictionarySchemaEntry(
      id = "rating.elo.weights",
      keyPattern = "rating.elo.<placementWeight|scoreWeight|umaWeight>",
      valueType = GlobalDictionaryValueType.Weight,
      description = "Relative weight components for placement, score, and uma in live ELO calculation.",
      validationHint = "Decimal between 0.0 and 1.0.",
      runtimeConsumers = Vector("rating-service"),
      examples = Vector("rating.elo.placementWeight", "rating.elo.scoreWeight", "rating.elo.umaWeight")
    ),
    GlobalDictionarySchemaEntry(
      id = "club.power.weights",
      keyPattern = "club.power.<eloWeight|pointWeight>",
      valueType = GlobalDictionaryValueType.Decimal,
      description = "Non-negative coefficients used by the live club power formula.",
      validationHint = "Non-negative decimal.",
      runtimeConsumers = Vector("club-projection", "public-club-leaderboard"),
      examples = Vector("club.power.eloWeight", "club.power.pointWeight")
    ),
    GlobalDictionarySchemaEntry(
      id = "club.power.baseBonus",
      keyPattern = "club.power.baseBonus",
      valueType = GlobalDictionaryValueType.Decimal,
      description = "Flat additive bonus in the live club power formula.",
      validationHint = "Finite decimal.",
      runtimeConsumers = Vector("club-projection", "public-club-leaderboard"),
      examples = Vector("club.power.baseBonus")
    ),
    GlobalDictionarySchemaEntry(
      id = "settlement.defaultPayoutRatios",
      keyPattern = "settlement.defaultPayoutRatios",
      valueType = GlobalDictionaryValueType.RatioVector,
      description = "Default payout ratios used when tournament settlement omits an explicit payout schema.",
      validationHint = "Comma- or semicolon-separated non-negative decimals with at least one positive value.",
      runtimeConsumers = Vector("tournament-settlement"),
      examples = Vector("settlement.defaultPayoutRatios")
    ),
    GlobalDictionarySchemaEntry(
      id = "rank.normalization.score",
      keyPattern = "rank.normalization.<platform>.<tier>[.<stars>|-<stars>]",
      valueType = GlobalDictionaryValueType.Integer,
      description = "Cross-platform rank normalization baseline or exact starred override used by the public player leaderboard.",
      validationHint = "Integer score.",
      runtimeConsumers = Vector("public-player-leaderboard"),
      examples = Vector("rank.normalization.tenhou.5-dan", "rank.normalization.mahjongsoul.master.2")
    ),
    GlobalDictionarySchemaEntry(
      id = "rank.normalization.starWeight",
      keyPattern = "rank.normalization.<platform>.starWeight",
      valueType = GlobalDictionaryValueType.Integer,
      description = "Per-star increment used when a platform rank uses baseline-plus-stars normalization.",
      validationHint = "Integer increment.",
      runtimeConsumers = Vector("public-player-leaderboard"),
      examples = Vector("rank.normalization.mahjongsoul.starWeight")
    ),
    GlobalDictionarySchemaEntry(
      id = "tournament.rule-template",
      keyPattern = "tournament.rule-template.<templateKey>",
      valueType = GlobalDictionaryValueType.StageRuleTemplate,
      description = "Reusable stage-rule template consumed by tournament rule normalization.",
      validationHint = "Semicolon-separated key=value directives such as advancement=SwissCut;cutSize=8.",
      runtimeConsumers = Vector("tournament-rule-normalization"),
      examples = Vector("tournament.rule-template.swiss-snake-template")
    ),
    MetadataSchema
  )

  val schemaView: GlobalDictionarySchemaView =
    GlobalDictionarySchemaView(
      entries = schemaEntries,
      unknownKeyPolicy = UnknownKeyPolicy
    )

  def resolve(key: String): ResolvedKey =
    val normalized = normalizeKey(key)
    normalized match
      case RatingKFactorKey =>
        known(schemaEntries(0), normalized) { value =>
          require(parseInt(value).exists(_ > 0), "rating.elo.kFactor must be a positive integer")
        }
      case RatingPlacementWeightKey | RatingScoreWeightKey | RatingUmaWeightKey =>
        known(schemaEntries(1), normalized) { value =>
          require(parseDouble(value).exists(weight => weight >= 0.0 && weight <= 1.0), s"$key must be a number between 0.0 and 1.0")
        }
      case ClubPowerEloWeightKey | ClubPowerPointWeightKey =>
        known(schemaEntries(2), normalized) { value =>
          require(parseDouble(value).exists(_ >= 0.0), s"$key must be a non-negative number")
        }
      case ClubPowerBaseBonusKey =>
        known(schemaEntries(3), normalized) { value =>
          require(parseDouble(value).nonEmpty, s"$key must be a valid number")
        }
      case SettlementPayoutRatiosKey =>
        known(schemaEntries(4), normalized) { value =>
          val ratios = parseDoubleVector(value)
          require(ratios.nonEmpty, "settlement.defaultPayoutRatios must contain at least one ratio")
          require(ratios.forall(_ >= 0.0), "settlement.defaultPayoutRatios cannot contain negative values")
          require(ratios.exists(_ > 0.0), "settlement.defaultPayoutRatios must contain a positive ratio")
        }
      case _ if isRankNormalizationStarWeight(normalized) =>
        known(schemaEntries(6), normalized) { value =>
          require(parseInt(value).nonEmpty, s"$normalized must be an integer")
        }
      case _ if normalized.startsWith(RankNormalizationPrefix) =>
        known(schemaEntries(5), normalized) { value =>
          require(parseInt(value).nonEmpty, s"$normalized must be an integer")
        }
      case _ if normalized.startsWith(TournamentRuleTemplatePrefix) =>
        known(schemaEntries(7), normalized) { value =>
          parseStageRuleTemplate(value)
          ()
        }
      case _ if isReservedRuntimeNamespace(normalized) =>
        throw IllegalArgumentException(
          s"Dictionary key $key is inside a reserved runtime namespace and must match a registered schema entry"
        )
      case _ =>
        MetadataResolvedKey(normalized)

  def validate(key: String, value: String): Unit =
    resolve(key).validate(value)

  def normalizeKey(key: String): String =
    key.trim.toLowerCase

  def normalizedToken(value: String): String =
    value.trim.toLowerCase.replaceAll("[^a-z0-9]+", "-").stripPrefix("-").stripSuffix("-")

  def normalizePlatformKey(platform: RankPlatform): String =
    normalizedToken(platform.toString)

  def stageRuleTemplateKey(templateKey: String): String =
    s"$TournamentRuleTemplatePrefix$templateKey"

  def normalizeNamespacePrefix(prefix: String): String =
    val normalized = normalizeKey(prefix).stripSuffix(".")
    val segments = normalized.split("\\.").toVector.map(_.trim).filter(_.nonEmpty)
    require(segments.nonEmpty, "Dictionary namespace prefix cannot be empty")
    require(
      segments.forall(segment => segment.matches("[a-z0-9-]+")),
      "Dictionary namespace segments must contain only lowercase letters, digits, or dashes"
    )
    val result = segments.mkString(".") + "."
    require(!isReservedRuntimeNamespace(result), "Reserved runtime namespaces cannot be claimed through metadata governance")
    result

  def metadataNamespacePrefixForKey(key: String): String =
    val normalized = normalizeKey(key)
    val segments = normalized.split("\\.").toVector.map(_.trim).filter(_.nonEmpty)
    require(segments.size >= 2, "Metadata keys must contain at least two segments to derive a namespace prefix")
    s"${segments.take(2).mkString(".")}."

  def isMetadataKey(key: String): Boolean =
    resolve(key).schemaEntry.valueType == GlobalDictionaryValueType.Metadata

  def parseStageRuleTemplate(value: String): StageRuleTemplate =
    val directives = value
      .split(";")
      .toVector
      .map(_.trim)
      .filter(_.nonEmpty)
      .map { directive =>
        val Array(rawKey, rawValue) = directive.split("=", 2)
        normalizedToken(rawKey) -> rawValue.trim
      }
      .toMap

    StageRuleTemplate(
      ruleType = directives.get("advancement").orElse(directives.get("advancementruletype")).map(parseAdvancementRuleType),
      cutSize = directives.get("cutsize").flatMap(parseInt),
      thresholdScore = directives.get("thresholdscore").flatMap(parseInt),
      targetTableCount = directives.get("targettablecount").flatMap(parseInt),
      schedulingPoolSize = directives.get("schedulingpoolsize").flatMap(parseInt),
      pairingMethod = directives.get("pairingmethod"),
      carryOverPoints = directives.get("carryoverpoints").flatMap(parseBoolean),
      maxRounds = directives.get("maxrounds").flatMap(parseInt),
      bracketSize = directives.get("bracketsize").flatMap(parseInt),
      thirdPlaceMatch = directives.get("thirdplacematch").flatMap(parseBoolean),
      repechageEnabled = directives.get("repechageenabled").flatMap(parseBoolean),
      seedingPolicy = directives.get("seedingpolicy"),
      note = directives.get("note").filter(_.nonEmpty)
    )

  private def isRankNormalizationStarWeight(normalizedKey: String): Boolean =
    normalizedKey.startsWith(RankNormalizationPrefix) && normalizedKey.endsWith(".starweight")

  private def isReservedRuntimeNamespace(normalizedKey: String): Boolean =
    ReservedRuntimePrefixes.exists(prefix => normalizedKey.startsWith(prefix))

  private def known(
      schemaEntry: GlobalDictionarySchemaEntry,
      normalizedKey: String
  )(
      validator: String => Unit
  ): ResolvedKey =
    KnownResolvedKey(schemaEntry, normalizedKey, validator)

  private def parseInt(value: String): Option[Int] =
    Try(value.trim.toInt).toOption

  private def parseDouble(value: String): Option[Double] =
    Try(value.trim.toDouble).toOption.filter(_.isFinite)

  private def parseDoubleVector(value: String): Vector[Double] =
    value
      .split("[,;]+")
      .toVector
      .map(_.trim)
      .filter(_.nonEmpty)
      .flatMap(parseDouble)

  private def parseBoolean(value: String): Option[Boolean] =
    value.trim.toLowerCase match
      case "true" | "yes" | "1"  => Some(true)
      case "false" | "no" | "0" => Some(false)
      case _                         => None

  private def parseAdvancementRuleType(value: String): AdvancementRuleType =
    AdvancementRuleType.values.find(rule => normalizedToken(rule.toString) == normalizedToken(value))
      .getOrElse(throw IllegalArgumentException(s"Unsupported advancement rule type: $value"))

