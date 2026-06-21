package riichinexus.microservices.club.objects.clubmanagement

/** 俱乐部聚合的稳定标识符。
  *
  * 用值类型包住字符串，避免在领域层把俱乐部 ID 与玩家、赛事或牌桌 ID 混传。
  */
final case class ClubId(value: String)
