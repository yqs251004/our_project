package riichinexus.microservices.tournament.domain.competition.model

import riichinexus.microservices.tournament.domain.stage.model.TournamentStage

import java.time.Instant

import riichinexus.microservices.player.objects.PlayerId
import riichinexus.microservices.club.objects.profile.ClubId
import riichinexus.microservices.tournament.objects.identity.TournamentId
import riichinexus.microservices.tournament.objects.competition.{TournamentStatus, TournamentWhitelistEntry}

import riichinexus.system.json.JsonCodecs.given

/** 赛事领域聚合的根状态。
  *
  * 它保存赛事基础信息、报名主体、管理员、白名单、阶段配置和当前状态，是赛程配置、公开展示和运营变更共同依赖的持久化对象。
  */
final case class Tournament(
    id: TournamentId,
    name: String,
    organizer: String,
    startsAt: Instant,
    endsAt: Instant,
    participatingClubs: Vector[ClubId] = Vector.empty,
    participatingPlayers: Vector[PlayerId] = Vector.empty,
    admins: Vector[PlayerId] = Vector.empty,
    whitelist: Vector[TournamentWhitelistEntry] = Vector.empty,
    stages: Vector[TournamentStage] = Vector.empty,
    status: TournamentStatus = TournamentStatus.Draft,
    version: Int = 0
)
