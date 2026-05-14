package riichinexus.microservices.dictionary.api

import riichinexus.application.ports.{GlobalDictionaryRepository, PlayerRepository}
import riichinexus.domain.model.*
import riichinexus.domain.service.{EloRatingConfig, GlobalDictionaryRegistry, RatingConfigProvider}

object RuntimeDictionarySupport:
  private val RatingKFactorKey = GlobalDictionaryRegistry.RatingKFactorKey
  private val RatingPlacementWeightKey = GlobalDictionaryRegistry.RatingPlacementWeightKey
  private val RatingScoreWeightKey = GlobalDictionaryRegistry.RatingScoreWeightKey
  private val RatingUmaWeightKey = GlobalDictionaryRegistry.RatingUmaWeightKey
  private val ClubPowerEloWeightKey = GlobalDictionaryRegistry.ClubPowerEloWeightKey
  private val ClubPowerPointWeightKey = GlobalDictionaryRegistry.ClubPowerPointWeightKey
  private val ClubPowerBaseBonusKey = GlobalDictionaryRegistry.ClubPowerBaseBonusKey
  private val SettlementPayoutRatiosKey = GlobalDictionaryRegistry.SettlementPayoutRatiosKey
  private val RankNormalizationPrefix = GlobalDictionaryRegistry.RankNormalizationPrefix

  final case class ClubPowerConfig(
      eloWeight: Double = 1.0,
      pointWeight: Double = 0.001,
      baseBonus: Double = 0.0
  )

  final case class NormalizedRank(
      score: Int,
      sourceKey: String
  )

  final case class DictionarySnapshot(
      valuesByNormalizedKey: Map[String, String]
  )

  def validateRuntimeEntry(
      key: String,
      value: String
  ): Unit =
    GlobalDictionaryRegistry.validate(key, value)

  def currentEloRatingConfig(
      globalDictionaryRepository: GlobalDictionaryRepository
  ): EloRatingConfig =
    currentEloRatingConfig(snapshot(globalDictionaryRepository))

  def currentEloRatingConfig(
      dictionarySnapshot: DictionarySnapshot
  ): EloRatingConfig =
    val defaultConfig = EloRatingConfig.default
    val kFactor = readInt(dictionarySnapshot, RatingKFactorKey).filter(_ > 0).getOrElse(defaultConfig.kFactor)
    val rawPlacementWeight = readDouble(dictionarySnapshot, RatingPlacementWeightKey).filter(_ >= 0.0).getOrElse(defaultConfig.placementWeight)
    val rawScoreWeight = readDouble(dictionarySnapshot, RatingScoreWeightKey).filter(_ >= 0.0).getOrElse(defaultConfig.scoreWeight)
    val rawUmaWeight = readDouble(dictionarySnapshot, RatingUmaWeightKey).filter(_ >= 0.0).getOrElse(defaultConfig.umaWeight)
    val totalWeight = rawPlacementWeight + rawScoreWeight + rawUmaWeight
    val normalizedWeights =
      if totalWeight <= 0.0 then
        (defaultConfig.placementWeight, defaultConfig.scoreWeight, defaultConfig.umaWeight)
      else
        (
          rawPlacementWeight / totalWeight,
          rawScoreWeight / totalWeight,
          rawUmaWeight / totalWeight
        )

    EloRatingConfig(
      kFactor = kFactor,
      placementWeight = normalizedWeights._1,
      scoreWeight = normalizedWeights._2,
      umaWeight = normalizedWeights._3
    )

  def currentClubPowerConfig(
      globalDictionaryRepository: GlobalDictionaryRepository
  ): ClubPowerConfig =
    currentClubPowerConfig(snapshot(globalDictionaryRepository))

  def currentClubPowerConfig(
      dictionarySnapshot: DictionarySnapshot
  ): ClubPowerConfig =
    ClubPowerConfig(
      eloWeight = readDouble(dictionarySnapshot, ClubPowerEloWeightKey).filter(_ >= 0.0).getOrElse(1.0),
      pointWeight = readDouble(dictionarySnapshot, ClubPowerPointWeightKey).filter(_ >= 0.0).getOrElse(0.001),
      baseBonus = readDouble(dictionarySnapshot, ClubPowerBaseBonusKey).getOrElse(0.0)
    )

  def currentSettlementPayoutRatios(
      globalDictionaryRepository: GlobalDictionaryRepository
  ): Vector[Double] =
    currentSettlementPayoutRatios(snapshot(globalDictionaryRepository))

  def currentSettlementPayoutRatios(
      dictionarySnapshot: DictionarySnapshot
  ): Vector[Double] =
    readDoubleVector(dictionarySnapshot, SettlementPayoutRatiosKey)
      .filter(_ >= 0.0) match
      case ratios if ratios.nonEmpty && ratios.exists(_ > 0.0) => ratios
      case _                                                   => Vector(0.5, 0.3, 0.2)

  def calculateClubPowerRating(
      club: Club,
      playerRepository: PlayerRepository,
      globalDictionaryRepository: GlobalDictionaryRepository
  ): Double =
    calculateClubPowerRating(club, playerRepository, snapshot(globalDictionaryRepository))

  def calculateClubPowerRating(
      club: Club,
      playerRepository: PlayerRepository,
      dictionarySnapshot: DictionarySnapshot
  ): Double =
    val memberElos = club.members.flatMap(memberId =>
      playerRepository.findById(memberId).filter(_.status == PlayerStatus.Active).map(_.elo)
    )
    val averageElo =
      if memberElos.isEmpty then 0.0 else memberElos.sum.toDouble / memberElos.size.toDouble
    val config = currentClubPowerConfig(dictionarySnapshot)
    round2(averageElo * config.eloWeight + club.totalPoints.toDouble * config.pointWeight + config.baseBonus)

  def normalizeRank(
      rank: RankSnapshot,
      globalDictionaryRepository: GlobalDictionaryRepository
  ): Option[NormalizedRank] =
    normalizeRank(rank, snapshot(globalDictionaryRepository))

  def normalizeRank(
      rank: RankSnapshot,
      dictionarySnapshot: DictionarySnapshot
  ): Option[NormalizedRank] =
    val platformKey = GlobalDictionaryRegistry.normalizePlatformKey(rank.platform)
    val normalizedTier = GlobalDictionaryRegistry.normalizedToken(rank.tier)
    val starSpecificKeys =
      rank.stars.toVector.flatMap { stars =>
        Vector(
          s"$RankNormalizationPrefix$platformKey.$normalizedTier.$stars",
          s"$RankNormalizationPrefix$platformKey.$normalizedTier-$stars"
        )
      }
    val baseKey = s"$RankNormalizationPrefix$platformKey.$normalizedTier"

    starSpecificKeys
      .flatMap(key => readInt(dictionarySnapshot, key).map(score => NormalizedRank(score, key)))
      .headOption
      .orElse {
        readInt(dictionarySnapshot, baseKey).map { base =>
          val weightedScore = rank.stars.flatMap { stars =>
            readInt(dictionarySnapshot, s"$RankNormalizationPrefix$platformKey.starweight")
              .map(starWeight => base + stars * starWeight)
          }.getOrElse(base)

          NormalizedRank(
            score = weightedScore,
            sourceKey = if rank.stars.nonEmpty then s"$RankNormalizationPrefix$platformKey.starweight" else baseKey
          )
        }
      }

  def resolveStageRules(
      stage: TournamentStage,
      globalDictionaryRepository: GlobalDictionaryRepository
  ): TournamentStage =
    resolveStageRules(stage, snapshot(globalDictionaryRepository))

  def resolveStageRules(
      stage: TournamentStage,
      dictionarySnapshot: DictionarySnapshot
  ): TournamentStage =
    stage.advancementRule.templateKey
      .flatMap(templateKey => readStageRuleTemplate(dictionarySnapshot, templateKey))
      .map(template => applyStageRuleTemplate(stage, template))
      .getOrElse(stage)

  def snapshot(
      globalDictionaryRepository: GlobalDictionaryRepository
  ): DictionarySnapshot =
    DictionarySnapshot(
      globalDictionaryRepository.findAll()
        .iterator
        .map(entry => normalizedKey(entry.key) -> entry.value.trim)
        .filter(_._2.nonEmpty)
        .toMap
    )

  private def readInt(
      globalDictionaryRepository: GlobalDictionaryRepository,
      key: String
  ): Option[Int] =
    readValue(globalDictionaryRepository, key).flatMap(parseInt)

  private def readInt(
      dictionarySnapshot: DictionarySnapshot,
      key: String
  ): Option[Int] =
    readValue(dictionarySnapshot, key).flatMap(parseInt)

  private def readDouble(
      globalDictionaryRepository: GlobalDictionaryRepository,
      key: String
  ): Option[Double] =
    readValue(globalDictionaryRepository, key).flatMap(parseDouble)

  private def readDouble(
      dictionarySnapshot: DictionarySnapshot,
      key: String
  ): Option[Double] =
    readValue(dictionarySnapshot, key).flatMap(parseDouble)

  private def readDoubleVector(
      globalDictionaryRepository: GlobalDictionaryRepository,
      key: String
  ): Vector[Double] =
    readValue(globalDictionaryRepository, key).map(parseDoubleVector).getOrElse(Vector.empty)

  private def readDoubleVector(
      dictionarySnapshot: DictionarySnapshot,
      key: String
  ): Vector[Double] =
    readValue(dictionarySnapshot, key).map(parseDoubleVector).getOrElse(Vector.empty)

  private def readValue(
      globalDictionaryRepository: GlobalDictionaryRepository,
      key: String
  ): Option[String] =
    globalDictionaryRepository.findAll()
      .find(entry => normalizedKey(entry.key) == normalizedKey(key))
      .map(_.value.trim)
      .filter(_.nonEmpty)

  private def readValue(
      dictionarySnapshot: DictionarySnapshot,
      key: String
  ): Option[String] =
    dictionarySnapshot.valuesByNormalizedKey.get(normalizedKey(key))

  private def parseInt(value: String): Option[Int] =
    scala.util.Try(value.trim.toInt).toOption

  private def parseDouble(value: String): Option[Double] =
    scala.util.Try(value.trim.toDouble).toOption.filter(_.isFinite)

  private def parseDoubleVector(value: String): Vector[Double] =
    value
      .split("[,;]+")
      .toVector
      .map(_.trim)
      .filter(_.nonEmpty)
      .flatMap(parseDouble)

  private def normalizedKey(key: String): String =
    GlobalDictionaryRegistry.normalizeKey(key)

  private def readStageRuleTemplate(
      globalDictionaryRepository: GlobalDictionaryRepository,
      templateKey: String
  ): Option[GlobalDictionaryRegistry.StageRuleTemplate] =
    readValue(globalDictionaryRepository, GlobalDictionaryRegistry.stageRuleTemplateKey(templateKey))
      .map(GlobalDictionaryRegistry.parseStageRuleTemplate)

  private def readStageRuleTemplate(
      dictionarySnapshot: DictionarySnapshot,
      templateKey: String
  ): Option[GlobalDictionaryRegistry.StageRuleTemplate] =
    readValue(dictionarySnapshot, GlobalDictionaryRegistry.stageRuleTemplateKey(templateKey))
      .map(GlobalDictionaryRegistry.parseStageRuleTemplate)

  private def applyStageRuleTemplate(
      stage: TournamentStage,
      template: GlobalDictionaryRegistry.StageRuleTemplate
  ): TournamentStage =
    val existingRule = stage.advancementRule
    val defaultRule = AdvancementRule.defaultFor(stage.format)
    val usingPlaceholderRule =
      existingRule.ruleType == AdvancementRuleType.Custom &&
        existingRule.cutSize.isEmpty &&
        existingRule.thresholdScore.isEmpty &&
        existingRule.targetTableCount.isEmpty &&
        existingRule.note.forall(note => note.trim.isEmpty || note == "unconfigured")

    val resolvedAdvancementRule = existingRule.copy(
      ruleType =
        if usingPlaceholderRule then template.ruleType.getOrElse(defaultRule.ruleType)
        else existingRule.ruleType,
      cutSize = existingRule.cutSize.orElse(template.cutSize),
      thresholdScore = existingRule.thresholdScore.orElse(template.thresholdScore),
      targetTableCount = existingRule.targetTableCount.orElse(template.targetTableCount),
      note = existingRule.note.filter(note => note.trim.nonEmpty && note != "unconfigured").orElse(template.note)
    )

    val resolvedSwissRule =
      stage.swissRule.orElse {
        if template.pairingMethod.isDefined || template.carryOverPoints.isDefined || template.maxRounds.isDefined then
          Some(
            SwissRuleConfig(
              pairingMethod = template.pairingMethod.getOrElse("balanced-elo"),
              carryOverPoints = template.carryOverPoints.getOrElse(true),
              maxRounds = template.maxRounds
            )
          )
        else None
      }

    val resolvedKnockoutRule =
      stage.knockoutRule.orElse {
        if template.bracketSize.isDefined || template.thirdPlaceMatch.isDefined ||
            template.repechageEnabled.isDefined || template.seedingPolicy.isDefined then
          Some(
            KnockoutRuleConfig(
              bracketSize = template.bracketSize,
              thirdPlaceMatch = template.thirdPlaceMatch.getOrElse(false),
              seedingPolicy = template.seedingPolicy.getOrElse("rating"),
              repechageEnabled = template.repechageEnabled.getOrElse(false)
            )
          )
        else None
      }

    stage.copy(
      advancementRule = resolvedAdvancementRule,
      swissRule = resolvedSwissRule,
      knockoutRule = resolvedKnockoutRule,
      schedulingPoolSize =
        if stage.schedulingPoolSize == 4 then template.schedulingPoolSize.getOrElse(stage.schedulingPoolSize)
        else stage.schedulingPoolSize
    )

  private def round2(value: Double): Double =
    BigDecimal(value).setScale(2, BigDecimal.RoundingMode.HALF_UP).toDouble

final class DictionaryBackedRatingConfigProvider(
    globalDictionaryRepository: GlobalDictionaryRepository
) extends RatingConfigProvider:
  override def current(): EloRatingConfig =
    RuntimeDictionarySupport.currentEloRatingConfig(globalDictionaryRepository)
