package riichinexus.microservices.club.objects.clubmanagement.apiTypes

import upickle.default.{ReadWriter, macroRW}

/** 公开详情页展示的一条俱乐部荣誉。
  *
  * 当前只暴露标题，保留独立类型是为了后续平滑增加达成时间、来源赛事或说明文案。
  */
final case class PublicClubHonorView(
    title: String
)

object PublicClubHonorView:
  given ReadWriter[PublicClubHonorView] = macroRW
