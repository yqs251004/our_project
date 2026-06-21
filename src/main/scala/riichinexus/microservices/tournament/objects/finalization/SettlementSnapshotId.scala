package riichinexus.microservices.tournament.objects.finalization

/** 赛事结算快照的稳定标识符。
  *
  * 结算可能产生多个修订版本，独立 ID 能让最终稿、草稿和被替代版本在审计与查询中彼此区分。
  */
final case class SettlementSnapshotId(value: String)
