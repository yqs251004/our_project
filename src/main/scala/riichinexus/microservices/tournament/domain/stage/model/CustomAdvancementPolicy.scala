package riichinexus.microservices.tournament.domain.stage.model

/** 自定义晋级规则解析后的筛选与保留席位策略。
  *
  * 晋级投影器使用该模型承载从阶段规则备注中解析出的条件，并据此计算晋级人数与摘要文案。
  */
private[tournament] final case class CustomAdvancementPolicy(
    topCount: Option[Int] = None,
    topPercent: Option[Double] = None,
    minMatches: Option[Int] = None,
    minPlacementPoints: Option[Int] = None,
    minScoreDelta: Option[Int] = None,
    minFinalPoints: Option[Int] = None,
    maxAveragePlacement: Option[Double] = None,
    reserveCount: Option[Int] = None,
    targetTableCount: Option[Int] = None
):
  def qualifyingLimit(totalEntries: Int): Int =
    topCount
      .orElse(targetTableCount.map(_ * 4))
      .orElse(topPercent.map(percent => math.ceil(totalEntries.toDouble * percent / 100.0).toInt))
      .getOrElse(totalEntries)

  def summary: String =
    Vector(
      topCount.map(value => s"top=$value"),
      topPercent.map(value => s"topPercent=${round2(value)}"),
      targetTableCount.map(value => s"targetTables=$value"),
      minMatches.map(value => s"minMatches=$value"),
      minPlacementPoints.map(value => s"placementPoints>=$value"),
      minScoreDelta.map(value => s"scoreDelta>=$value"),
      minFinalPoints.map(value => s"finalPoints>=$value"),
      maxAveragePlacement.map(value => s"averagePlacement<=${round2(value)}"),
      reserveCount.map(value => s"reserve=$value")
    ).flatten.mkString(", ")

  private def round2(value: Double): Double =
    BigDecimal(value).setScale(2, BigDecimal.RoundingMode.HALF_UP).toDouble
