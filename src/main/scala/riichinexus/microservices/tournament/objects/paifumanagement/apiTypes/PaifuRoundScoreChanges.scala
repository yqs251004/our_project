package riichinexus.microservices.tournament.objects.paifumanagement.apiTypes

import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.tournament.objects.paifumanagement.{KyokuDescriptor, ScoreChange}
import upickle.default.*

final case class PaifuRoundScoreChanges(
    descriptor: KyokuDescriptor,
    scoreChanges: Vector[ScoreChange]
) derives CanEqual, ReadWriter
