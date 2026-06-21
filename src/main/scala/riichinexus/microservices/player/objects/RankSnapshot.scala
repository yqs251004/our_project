package riichinexus.microservices.player.objects

/** 玩家当前段位在外部平台上的快照。
  *
  * `tier` 保存平台原始段位文本，`stars` 保存可选星级，让前端可以展示原貌，后端也能进一步计算标准化段位分。
  */
final case class RankSnapshot(
    platform: RankPlatform,
    tier: String,
    stars: Option[Int] = None
)
