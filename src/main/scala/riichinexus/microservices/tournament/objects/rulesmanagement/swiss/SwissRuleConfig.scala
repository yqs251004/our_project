package riichinexus.microservices.tournament.objects.rulesmanagement.swiss

final case class SwissRuleConfig(
    pairingMethod: String = "balanced-elo",
    carryOverPoints: Boolean = true,
    maxRounds: Option[Int] = None
) derives CanEqual
