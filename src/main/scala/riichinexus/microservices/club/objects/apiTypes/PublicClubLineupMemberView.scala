package riichinexus.microservices.club.objects.apiTypes

import riichinexus.domain.model.PlayerId
import riichinexus.microservices.club.objects.ClubPrivilegeCode
import riichinexus.microservices.player.objects.PlayerStatus
import riichinexus.microservices.tournament.objects.apiTypes.RankSnapshotView
import upickle.default.*

final case class PublicClubLineupMemberView(
    playerId: String,
    nickname: String,
    elo: Int,
    currentRank: RankSnapshotView,
    status: String,
    isAdmin: Boolean,
    internalTitle: Option[String],
    privileges: Vector[ClubPrivilegeCode]
) derives CanEqual

object PublicClubLineupMemberView:
  given ReadWriter[PublicClubLineupMemberView] = macroRW

  def apply(
      playerId: PlayerId,
      nickname: String,
      elo: Int,
      currentRank: RankSnapshotView,
      status: PlayerStatus,
      isAdmin: Boolean,
      internalTitle: Option[String],
      privileges: Vector[ClubPrivilegeCode]
  ): PublicClubLineupMemberView =
    PublicClubLineupMemberView(
      playerId = playerId.value,
      nickname = nickname,
      elo = elo,
      currentRank = currentRank,
      status = status.toString,
      isAdmin = isAdmin,
      internalTitle = internalTitle,
      privileges = privileges
    )
