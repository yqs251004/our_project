package riichinexus.microservices.tournament.objects.apiTypes

import riichinexus.domain.model.Yaku

final case class TournamentPaifuYakuView(
    name: String,
    han: Int
) derives CanEqual

object TournamentPaifuYakuView:
  def fromDomain(yaku: Yaku): TournamentPaifuYakuView =
    TournamentPaifuYakuView(
      name = yaku.name,
      han = yaku.han
    )
