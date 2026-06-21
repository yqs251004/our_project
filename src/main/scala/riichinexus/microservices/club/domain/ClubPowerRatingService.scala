package riichinexus.microservices.club.domain

import riichinexus.microservices.player.objects.playerprofile.PlayerId

import riichinexus.microservices.player.objects.`private`.PlayerPrivateView
import riichinexus.microservices.player.objects.PlayerStatus

/** 俱乐部实力评分的领域计算服务。
  *
  * 服务根据活跃成员平均 ELO、俱乐部总积分和可配置权重计算展示用实力值，供排行榜与后台分析使用。
  */

private[club] object ClubPowerRatingService:
  /** 实力评分公式中的权重配置。 */
  final case class Config(
      eloWeight: Double = 1.0,
      pointWeight: Double = 0.001,
      baseBonus: Double = 0.0
  )

  def calculate(
      club: Club,
      findPlayer: PlayerId => Option[PlayerPrivateView],
      config: Config = Config()
  ): Double =
    val memberElos = club.members.flatMap(memberId =>
      findPlayer(memberId).filter(_.status == PlayerStatus.Active).map(_.elo)
    )
    val averageElo =
      if memberElos.isEmpty then 0.0 else memberElos.sum.toDouble / memberElos.size.toDouble

    round2(averageElo * config.eloWeight + club.totalPoints.toDouble * config.pointWeight + config.baseBonus)

  private def round2(value: Double): Double =
    BigDecimal(value).setScale(2, BigDecimal.RoundingMode.HALF_UP).toDouble
