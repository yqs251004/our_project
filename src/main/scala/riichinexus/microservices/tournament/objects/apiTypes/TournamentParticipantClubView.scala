package riichinexus.microservices.tournament.objects.apiTypes

import riichinexus.domain.model.ClubId

final case class TournamentParticipantClubView(
    clubId: String,
    memberCount: Int
) derives CanEqual

object TournamentParticipantClubView:
  def apply(clubId: ClubId, memberCount: Int): TournamentParticipantClubView =
    TournamentParticipantClubView(clubId.value, memberCount)
