package riichinexus.microservices.tournament.objects.apiTypes

import riichinexus.domain.model.KyokuDescriptor

final case class TournamentPaifuRoundDescriptorView(
    roundWind: String,
    handNumber: Int,
    honba: Int
) derives CanEqual

object TournamentPaifuRoundDescriptorView:
  def fromDomain(descriptor: KyokuDescriptor): TournamentPaifuRoundDescriptorView =
    TournamentPaifuRoundDescriptorView(
      roundWind = descriptor.roundWind.toString,
      handNumber = descriptor.handNumber,
      honba = descriptor.honba
    )
