package riichinexus.microservices.tournament.objects

import riichinexus.domain.model.*

final case class TournamentListQuery(
    status: Option[TournamentStatus] = None,
    adminId: Option[PlayerId] = None,
    organizer: Option[String] = None
)

final case class TournamentWhitelistQuery(
    participantKind: Option[TournamentParticipantKind] = None,
    playerId: Option[PlayerId] = None,
    clubId: Option[ClubId] = None
)

final case class TournamentSettlementQuery(
    stageId: Option[TournamentStageId] = None,
    status: Option[TournamentSettlementStatus] = None,
    championId: Option[PlayerId] = None
)

final case class StageTableQuery(
    status: Option[TableStatus] = None,
    roundNumber: Option[Int] = None,
    playerId: Option[PlayerId] = None
)

final case class TableListQuery(
    status: Option[TableStatus] = None,
    tournamentId: Option[TournamentId] = None,
    stageId: Option[TournamentStageId] = None,
    roundNumber: Option[Int] = None,
    playerId: Option[PlayerId] = None
)

final case class MatchRecordListQuery(
    playerId: Option[PlayerId] = None,
    tournamentId: Option[TournamentId] = None,
    stageId: Option[TournamentStageId] = None,
    tableId: Option[TableId] = None
)

final case class PaifuListQuery(
    playerId: Option[PlayerId] = None,
    tournamentId: Option[TournamentId] = None,
    stageId: Option[TournamentStageId] = None,
    tableId: Option[TableId] = None
)
