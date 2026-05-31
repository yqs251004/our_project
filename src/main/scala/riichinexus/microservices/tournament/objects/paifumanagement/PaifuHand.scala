package riichinexus.microservices.tournament.objects.paifumanagement

final case class PaifuHand(
    tiles: Vector[PaifuTile]
) derives CanEqual
