package riichinexus.microservices.tournament.objects.competition.apiTypes

import upickle.default.{ReadWriter, macroRW}

import riichinexus.microservices.club.objects.clubmanagement.ClubId

/** TournamentParticipantClubView 表示赛事参赛方俱乐部视图 的前端展示视图，包含俱乐部 ID、memberCount。 */

final case class TournamentParticipantClubView(
    clubId: String,
    memberCount: Int
)

object TournamentParticipantClubView:
  given ReadWriter[TournamentParticipantClubView] = macroRW

  def apply(clubId: ClubId, memberCount: Int): TournamentParticipantClubView =
    TournamentParticipantClubView(clubId.value, memberCount)
