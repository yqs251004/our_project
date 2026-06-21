package riichinexus.microservices.tournament.objects.competition.apiTypes

import upickle.default.{ReadWriter, macroRW}

import java.time.Instant

import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.microservices.club.objects.clubmanagement.ClubId
import riichinexus.microservices.tournament.objects.identity.TournamentId
import riichinexus.microservices.tournament.objects.competition.TournamentStatus
import riichinexus.microservices.tournament.objects.stage.apiTypes.TournamentStageSummaryView
import riichinexus.system.json.TournamentJsonCodecs.given

/** 运营后台赛事列表使用的摘要视图。
  *
  * 它保留参赛俱乐部、参赛玩家、管理员、白名单数量和阶段摘要，让后台列表可以直接展示赛事规模与管理入口。
  */
final case class TournamentSummaryView(
    tournamentId: String,
    name: String,
    organizer: String,
    startsAt: String,
    endsAt: String,
    status: TournamentStatus,
    participatingClubIds: Vector[String],
    participatingPlayerIds: Vector[String],
    adminIds: Vector[String],
    whitelistCount: Int,
    stages: Vector[TournamentStageSummaryView]
)

object TournamentSummaryView:
  given ReadWriter[TournamentSummaryView] = macroRW

  def apply(
      tournamentId: TournamentId,
      name: String,
      organizer: String,
      startsAt: Instant,
      endsAt: Instant,
      status: TournamentStatus,
      participatingClubIds: Vector[ClubId],
      participatingPlayerIds: Vector[PlayerId],
      adminIds: Vector[PlayerId],
      whitelistCount: Int,
      stages: Vector[TournamentStageSummaryView]
  ): TournamentSummaryView =
    TournamentSummaryView(
      tournamentId = tournamentId.value,
      name = name,
      organizer = organizer,
      startsAt = startsAt.toString,
      endsAt = endsAt.toString,
      status = status,
      participatingClubIds = participatingClubIds.map(_.value),
      participatingPlayerIds = participatingPlayerIds.map(_.value),
      adminIds = adminIds.map(_.value),
      whitelistCount = whitelistCount,
      stages = stages
    )
