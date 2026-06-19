package riichinexus.microservices.club.objects.clubmanagement.apiTypes

import upickle.default.{ReadWriter, macroRW}

/** PublicClubHonorView 表示公开俱乐部荣誉视图 的前端展示视图。 */

final case class PublicClubHonorView(
    title: String
)

object PublicClubHonorView:
  given ReadWriter[PublicClubHonorView] = macroRW
