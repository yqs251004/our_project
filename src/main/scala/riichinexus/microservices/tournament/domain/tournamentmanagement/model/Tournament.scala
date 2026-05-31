package riichinexus.microservices.tournament.domain.tournamentmanagement.model

import riichinexus.microservices.tournament.domain.lineupmanagement.model.*
import riichinexus.microservices.tournament.domain.recordmanagement.model.*
import riichinexus.microservices.tournament.domain.settlementmanagement.model.*
import riichinexus.microservices.tournament.domain.tablemanagement.model.*
import riichinexus.microservices.tournament.domain.tournamentmanagement.model.*
import riichinexus.microservices.tournament.domain.lineupmanagement.functions.*
import riichinexus.microservices.tournament.domain.paifumanagement.functions.*
import riichinexus.microservices.tournament.domain.recordmanagement.functions.*
import riichinexus.microservices.tournament.domain.rulesmanagement.functions.*
import riichinexus.microservices.tournament.domain.rulesmanagement.functions.knockout.*
import riichinexus.microservices.tournament.domain.rulesmanagement.functions.ranking.*
import riichinexus.microservices.tournament.domain.rulesmanagement.functions.stageprogression.*
import riichinexus.microservices.tournament.domain.rulesmanagement.functions.swiss.*
import riichinexus.microservices.tournament.domain.settlementmanagement.functions.*
import riichinexus.microservices.tournament.domain.tablemanagement.functions.*
import riichinexus.microservices.tournament.domain.tournamentmanagement.functions.*
import java.time.Instant

import riichinexus.domain.model.*
import riichinexus.microservices.tournament.objects.tournamentmanagement.{StageStatus, TournamentParticipantKind, TournamentStatus, TournamentWhitelistEntry}

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
) derives CanEqual
