package riichinexus.microservices.tournament.objects.apiTypes

import riichinexus.domain.model.TournamentWhitelistEntry

final case class TournamentWhitelistEntryView(
    participantKind: String,
    playerId: Option[String],
    clubId: Option[String]
) derives CanEqual

object TournamentWhitelistEntryView:
  def fromDomain(entry: TournamentWhitelistEntry): TournamentWhitelistEntryView =
    TournamentWhitelistEntryView(
      entry.participantKind.toString,
      entry.playerId.map(_.value),
      entry.clubId.map(_.value)
    )
