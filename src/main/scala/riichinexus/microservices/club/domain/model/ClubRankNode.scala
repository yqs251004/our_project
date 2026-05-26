package riichinexus.microservices.club.domain.model

final case class ClubRankNode(
    code: String,
    label: String,
    minimumContribution: Int,
    privileges: Vector[String] = Vector.empty
) derives CanEqual
