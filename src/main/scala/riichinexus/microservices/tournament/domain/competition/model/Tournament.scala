package riichinexus.microservices.tournament.domain.competition.model

import riichinexus.microservices.tournament.domain.stage.model.TournamentStage

import java.time.Instant

import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.microservices.club.objects.clubmanagement.ClubId
import riichinexus.microservices.tournament.objects.tournamentmanagement.TournamentId
import riichinexus.microservices.tournament.objects.tournamentmanagement.{TournamentStatus, TournamentWhitelistEntry}

import riichinexus.system.json.JsonCodecs.given
/** Tournament 表示后端领域中的赛事状态或规则，包含 ID、名称、organizer、startsAt、endsAt、participatingClubs等。 */
final case class Tournament(
    id: TournamentId,
    name: String,
    organizer: String,
    startsAt: Instant,
    endsAt: Instant,
    participatingClubs: Vector[ClubId] = Vector.empty,
    participatingPlayers: Vector[PlayerId] = Vector.empty,
    admins: Vector[PlayerId] = Vector.empty,
    whitelist: Vector[TournamentWhitelistEntry] = Vector.empty,
    stages: Vector[TournamentStage] = Vector.empty,
    status: TournamentStatus = TournamentStatus.Draft,
    version: Int = 0
)