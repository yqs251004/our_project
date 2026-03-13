package riichinexus.domain.model

import java.time.Instant
import java.util.UUID

final case class PlayerId(value: String) derives CanEqual
final case class ClubId(value: String) derives CanEqual
final case class TournamentId(value: String) derives CanEqual
final case class TournamentStageId(value: String) derives CanEqual
final case class TableId(value: String) derives CanEqual
final case class PaifuId(value: String) derives CanEqual

object IdGenerator:
  private def nextId(prefix: String): String =
    s"$prefix-${UUID.randomUUID().toString.take(8)}"

  def playerId(): PlayerId = PlayerId(nextId("player"))
  def clubId(): ClubId = ClubId(nextId("club"))
  def tournamentId(): TournamentId = TournamentId(nextId("tournament"))
  def stageId(): TournamentStageId = TournamentStageId(nextId("stage"))
  def tableId(): TableId = TableId(nextId("table"))
  def paifuId(): PaifuId = PaifuId(nextId("paifu"))

enum RankPlatform derives CanEqual:
  case Tenhou
  case MahjongSoul
  case Custom

final case class RankSnapshot(
    platform: RankPlatform,
    tier: String,
    stars: Option[Int] = None
) derives CanEqual

final case class Player(
    id: PlayerId,
    userId: String,
    nickname: String,
    registeredAt: Instant,
    currentRank: RankSnapshot,
    elo: Int,
    clubId: Option[ClubId] = None
) derives CanEqual:
  def joinClub(newClubId: ClubId): Player =
    copy(clubId = Some(newClubId))

  def leaveClub: Player =
    copy(clubId = None)

  def updateRank(rank: RankSnapshot): Player =
    copy(currentRank = rank)

  def applyElo(delta: Int): Player =
    copy(elo = elo + delta)

final case class ClubHonor(
    title: String,
    achievedAt: Instant,
    note: Option[String] = None
) derives CanEqual

final case class Club(
    id: ClubId,
    name: String,
    creator: PlayerId,
    createdAt: Instant,
    members: Vector[PlayerId] = Vector.empty,
    totalPoints: Int = 0,
    honors: Vector[ClubHonor] = Vector.empty
) derives CanEqual:
  def addMember(playerId: PlayerId): Club =
    if members.contains(playerId) then this
    else copy(members = members :+ playerId)

  def removeMember(playerId: PlayerId): Club =
    copy(members = members.filterNot(_ == playerId))

  def addPoints(points: Int): Club =
    copy(totalPoints = totalPoints + points)

  def addHonor(honor: ClubHonor): Club =
    copy(honors = honors :+ honor)
