package riichinexus.microservices.tournament.domain.tournamentmanagement.functions

import riichinexus.domain.model.*
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
import riichinexus.microservices.tournament.objects.tournamentmanagement.TournamentWhitelistEntry
import riichinexus.microservices.tournament.objects.tournamentmanagement.TournamentParticipantKind

object TournamentWhitelistEntryFunctions:
  def validate(entry: TournamentWhitelistEntry): Unit =
    require(
      entry.participantKind match
        case TournamentParticipantKind.Club   => entry.clubId.nonEmpty && entry.playerId.isEmpty
        case TournamentParticipantKind.Player => entry.playerId.nonEmpty && entry.clubId.isEmpty,
      s"Invalid whitelist entry for ${entry.participantKind}"
    )
