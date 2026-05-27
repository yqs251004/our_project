package riichinexus.microservices.tournament.objects.apiTypes

import upickle.default.*

import riichinexus.microservices.tournament.domain.model.Yaku

final case class TournamentPaifuYakuView(
    name: String,
    han: Int
) derives CanEqual

object TournamentPaifuYakuView:
  given ReadWriter[TournamentPaifuYakuView] = macroRW

  def fromDomain(yaku: Yaku): TournamentPaifuYakuView =
    TournamentPaifuYakuView(
      name = yaku.name,
      han = yaku.han
    )
