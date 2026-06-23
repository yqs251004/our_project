package riichinexus.microservices.club.domain.profile.model

/** 俱乐部实力评分公式的权重配置。
  *
  * 该模型只用于俱乐部领域内部计算，将成员 ELO、俱乐部总积分和基础加成折算为展示用实力评分。
  */
private[club] final case class ClubPowerRatingConfig(
    eloWeight: Double = 1.0,
    pointWeight: Double = 0.001,
    baseBonus: Double = 0.0
)
