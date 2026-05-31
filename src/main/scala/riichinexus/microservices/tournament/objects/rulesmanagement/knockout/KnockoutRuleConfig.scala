package riichinexus.microservices.tournament.objects.rulesmanagement.knockout

final case class KnockoutRuleConfig(
    bracketSize: Option[Int] = None,
    thirdPlaceMatch: Boolean = false,
    seedingPolicy: String = "rating",
    repechageEnabled: Boolean = false
) derives CanEqual
