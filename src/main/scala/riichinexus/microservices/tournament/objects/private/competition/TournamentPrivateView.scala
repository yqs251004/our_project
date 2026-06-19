package riichinexus.microservices.tournament.objects.`private`.competition

import java.time.Instant

import riichinexus.microservices.club.objects.clubmanagement.ClubId
import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.microservices.tournament.objects.identity.TournamentId
import riichinexus.microservices.tournament.objects.competition.{TournamentStatus, TournamentWhitelistEntry}
import riichinexus.microservices.tournament.objects.`private`.stage.TournamentStagePrivateView

/** TournamentPrivateView 表示后端内部使用的赛事后端内部视图 read model，包含 ID、名称、startsAt、endsAt、participatingClubs、participatingPlayers等。 */

final case class TournamentPrivateView(
    id: TournamentId,
    name: String,
    startsAt: Instant,
    endsAt: Instant,
    participatingClubs: Vector[ClubId],
    participatingPlayers: Vector[PlayerId],
    whitelist: Vector[TournamentWhitelistEntry],
    stages: Vector[TournamentStagePrivateView],
    status: TournamentStatus
)
