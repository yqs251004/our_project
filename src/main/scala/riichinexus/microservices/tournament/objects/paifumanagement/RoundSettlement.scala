package riichinexus.microservices.tournament.objects.paifumanagement

final case class RoundSettlement(
    riichiSticksDelta: Int = 0,
    honbaPayment: Int = 0,
    notes: Vector[String] = Vector.empty
)
