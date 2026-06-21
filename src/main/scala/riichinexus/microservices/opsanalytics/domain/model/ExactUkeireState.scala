package riichinexus.microservices.opsanalytics.domain.model

import riichinexus.system.json.JsonCodecs.given

/** 分析一局牌谱时跟踪有效进张样本的临时状态。
  *
  * `hand` 和公开可见牌决定当前样本是否可严格计算，`samples` 收集每个可追踪节点的进张数量。
  */
final case class ExactUkeireState(
    hand: Vector[Int],
    visibleKnown: Vector[Int],
    samples: Vector[Int],
    trackable: Boolean
)
