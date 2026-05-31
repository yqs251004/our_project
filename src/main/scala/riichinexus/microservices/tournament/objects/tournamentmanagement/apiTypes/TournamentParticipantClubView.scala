package riichinexus.microservices.tournament.objects.tournamentmanagement.apiTypes

import upickle.default.*

import riichinexus.domain.model.ClubId

final case class TournamentParticipantClubView(
    clubId: String,
    memberCount: Int
) derives CanEqual

object TournamentParticipantClubView:
  given ReadWriter[TournamentParticipantClubView] = macroRW

  def apply(clubId: ClubId, memberCount: Int): TournamentParticipantClubView =
    TournamentParticipantClubView(clubId.value, memberCount)
