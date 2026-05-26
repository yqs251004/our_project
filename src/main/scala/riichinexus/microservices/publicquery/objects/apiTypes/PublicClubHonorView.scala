package riichinexus.microservices.publicquery.objects.apiTypes

import riichinexus.microservices.club.domain.model.ClubHonor
import upickle.default.*

final case class PublicClubHonorView(
    title: String
) derives CanEqual

object PublicClubHonorView:
  given ReadWriter[PublicClubHonorView] = macroRW

  def fromDomain(honor: ClubHonor): PublicClubHonorView =
    PublicClubHonorView(title = honor.title)
