package riichinexus.microservices.tournament.domain.competition.functions

import riichinexus.microservices.tournament.domain.competition.model.Tournament
import riichinexus.microservices.tournament.domain.matchrecord.model.{MatchRecord, MatchRecordSeatResult}
import riichinexus.microservices.tournament.domain.stage.model.{StageLineupSeat, StageLineupSubmission, TournamentStage}
import riichinexus.microservices.tournament.objects.competition.`private`.TournamentPrivateView
import riichinexus.microservices.tournament.objects.matchrecord.`private`.MatchRecordPrivateView
import riichinexus.microservices.tournament.objects.matchrecord.`private`.MatchRecordSeatResultPrivateView
import riichinexus.microservices.tournament.objects.stage.`private`.StageLineupSeatPrivateView
import riichinexus.microservices.tournament.objects.stage.`private`.StageLineupSubmissionPrivateView
import riichinexus.microservices.tournament.objects.stage.`private`.TournamentStagePrivateView

/** TournamentPrivateViewFunctions 将赛事领域模型转换为后端内部 private read model。 */
private[tournament] object TournamentPrivateViewFunctions:
  def fromTournament(tournament: Tournament): TournamentPrivateView =
    TournamentPrivateView(
      id = tournament.id,
      name = tournament.name,
      startsAt = tournament.startsAt,
      endsAt = tournament.endsAt,
      participatingClubs = tournament.participatingClubs,
      participatingPlayers = tournament.participatingPlayers,
      whitelist = tournament.whitelist,
      stages = tournament.stages.map(fromStage),
      status = tournament.status
    )

  def fromMatchRecord(record: MatchRecord): MatchRecordPrivateView =
    MatchRecordPrivateView(
      id = record.id,
      tableId = record.tableId,
      tournamentId = record.tournamentId,
      stageId = record.stageId,
      generatedAt = record.generatedAt,
      seatResults = record.seatResults.map(fromSeatResult)
    )

  private def fromStage(stage: TournamentStage): TournamentStagePrivateView =
    TournamentStagePrivateView(
      id = stage.id,
      name = stage.name,
      order = stage.order,
      status = stage.status,
      lineupSubmissions = stage.lineupSubmissions.map(fromLineupSubmission)
    )

  private def fromLineupSubmission(submission: StageLineupSubmission): StageLineupSubmissionPrivateView =
    StageLineupSubmissionPrivateView(
      id = submission.id,
      clubId = submission.clubId,
      submittedBy = submission.submittedBy,
      submittedAt = submission.submittedAt,
      seats = submission.seats.map(fromLineupSeat),
      note = submission.note
    )

  private def fromLineupSeat(seat: StageLineupSeat): StageLineupSeatPrivateView =
    StageLineupSeatPrivateView(
      playerId = seat.playerId,
      reserve = seat.reserve
    )

  private def fromSeatResult(result: MatchRecordSeatResult): MatchRecordSeatResultPrivateView =
    MatchRecordSeatResultPrivateView(
      playerId = result.playerId,
      seat = result.seat,
      clubId = result.clubId,
      finalPoints = result.finalPoints,
      placement = result.placement,
      scoreDelta = result.scoreDelta,
      uma = result.uma,
      oka = result.oka
    )
