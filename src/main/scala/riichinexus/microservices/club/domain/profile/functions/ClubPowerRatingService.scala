package riichinexus.microservices.club.domain.profile.functions

import riichinexus.microservices.club.domain.profile.model.{Club, ClubPowerRatingConfig}
import riichinexus.microservices.player.objects.PlayerId

import riichinexus.microservices.player.objects.`private`.PlayerPrivateView
import riichinexus.microservices.player.objects.PlayerStatus

/** 俱乐部实力评分的领域计算服务。
  *
  * 服务根据活跃成员平均 ELO、俱乐部总积分和可配置权重计算展示用实力值，供排行榜与后台分析使用。
  */

private[club] object ClubPowerRatingService:
  def calculate(
      club: Club,
      findPlayer: PlayerId => Option[PlayerPrivateView],
      config: ClubPowerRatingConfig = ClubPowerRatingConfig()
  ): Double =
    val memberElos = club.members.flatMap(memberId =>
      findPlayer(memberId).filter(_.status == PlayerStatus.Active).map(_.elo)
    )
    val averageElo =
      if memberElos.isEmpty then 0.0 else memberElos.sum.toDouble / memberElos.size.toDouble

    round2(averageElo * config.eloWeight + club.totalPoints.toDouble * config.pointWeight + config.baseBonus)

  private def round2(value: Double): Double =
    BigDecimal(value).setScale(2, BigDecimal.RoundingMode.HALF_UP).toDouble
