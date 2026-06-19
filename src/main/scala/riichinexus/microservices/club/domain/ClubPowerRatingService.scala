package riichinexus.microservices.club.domain

import riichinexus.microservices.player.objects.playerprofile.PlayerId

import riichinexus.microservices.player.objects.`private`.PlayerPrivateView
import riichinexus.microservices.player.objects.PlayerStatus

/** ClubPowerRatingService 编排俱乐部实力评级服务 相关的领域流程和规则判断。 */

private[club] object ClubPowerRatingService:
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
