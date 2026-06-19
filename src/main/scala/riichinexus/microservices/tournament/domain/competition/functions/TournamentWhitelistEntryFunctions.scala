package riichinexus.microservices.tournament.domain.competition.functions


import riichinexus.microservices.tournament.objects.tournamentmanagement.TournamentWhitelistEntry
import riichinexus.microservices.tournament.objects.tournamentmanagement.TournamentParticipantKind

/** TournamentWhitelistEntryFunctions 提供赛事白名单条目相关的领域计算、校验和转换函数。 */

private[tournament] object TournamentWhitelistEntryFunctions:
  def validate(entry: TournamentWhitelistEntry): Unit =
    require(
      entry.participantKind match
        case TournamentParticipantKind.Club   => entry.clubId.nonEmpty && entry.playerId.isEmpty
        case TournamentParticipantKind.Player => entry.playerId.nonEmpty && entry.clubId.isEmpty,
      s"Invalid whitelist entry for ${entry.participantKind}"
    )
