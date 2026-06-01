package riichinexus.microservices.club.objects.clubmanagement.apiTypes

import upickle.default.*

final case class PublicClubHonorView(
    title: String
) derives CanEqual

object PublicClubHonorView:
  given ReadWriter[PublicClubHonorView] = macroRW
