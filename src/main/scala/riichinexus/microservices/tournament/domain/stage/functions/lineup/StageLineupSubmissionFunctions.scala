package riichinexus.microservices.tournament.domain.stage.functions.lineup

import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.microservices.tournament.domain.stage.model.StageLineupSubmission


/** StageLineupSubmissionFunctions 提供阶段阵容提交相关的领域计算、校验和转换函数。 */


private[tournament] object StageLineupSubmissionFunctions:
  def validate(submission: StageLineupSubmission): Unit =
    require(submission.seats.nonEmpty, "Lineup submission must contain at least one seat")
    require(
      submission.seats.map(_.playerId).distinct.size == submission.seats.size,
      "Lineup submission cannot contain duplicate players"
    )
    require(
      submission.seats.exists(seat => !seat.reserve),
      "Lineup submission must contain at least one active player"
    )

  def activePlayerIds(submission: StageLineupSubmission): Vector[PlayerId] =
    submission.seats.filterNot(_.reserve).map(_.playerId)
