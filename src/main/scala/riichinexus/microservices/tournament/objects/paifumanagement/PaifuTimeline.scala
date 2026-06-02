package riichinexus.microservices.tournament.objects.paifumanagement

final case class PaifuTimeline(
    events: Vector[PaifuAction]
)
