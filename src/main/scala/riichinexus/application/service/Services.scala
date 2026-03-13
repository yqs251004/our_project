package riichinexus.application.service

import java.time.Instant

import riichinexus.application.ports.*
import riichinexus.domain.event.*
import riichinexus.domain.model.*
import riichinexus.domain.service.*

final class PlayerApplicationService(
    playerRepository: PlayerRepository,
    transactionManager: TransactionManager = NoOpTransactionManager
):
  def registerPlayer(
      userId: String,
      nickname: String,
      rank: RankSnapshot,
      registeredAt: Instant = Instant.now(),
      initialElo: Int = 1500
  ): Player =
    transactionManager.inTransaction {
      val player = playerRepository.findByUserId(userId) match
        case Some(existing) =>
          existing.copy(
            nickname = nickname,
            currentRank = rank
          )
        case None =>
          Player(
            id = IdGenerator.playerId(),
            userId = userId,
            nickname = nickname,
            registeredAt = registeredAt,
            currentRank = rank,
            elo = initialElo
          )

      playerRepository.save(player)
    }

final class ClubApplicationService(
    clubRepository: ClubRepository,
    playerRepository: PlayerRepository,
    transactionManager: TransactionManager = NoOpTransactionManager
):
  def createClub(
      name: String,
      creatorId: PlayerId,
      createdAt: Instant = Instant.now()
  ): Club =
    transactionManager.inTransaction {
      val club = clubRepository.findByName(name) match
        case Some(existing) =>
          if existing.members.contains(creatorId) then existing
          else existing.addMember(creatorId)
        case None =>
          Club(
            id = IdGenerator.clubId(),
            name = name,
            creator = creatorId,
            createdAt = createdAt,
            members = Vector(creatorId)
          )

      playerRepository.findById(creatorId).foreach { creator =>
        playerRepository.save(creator.joinClub(club.id))
      }

      clubRepository.save(club)
    }

  def addMember(clubId: ClubId, playerId: PlayerId): Option[Club] =
    transactionManager.inTransaction {
      for
        club <- clubRepository.findById(clubId)
        player <- playerRepository.findById(playerId)
      yield
        playerRepository.save(player.joinClub(clubId))
        clubRepository.save(club.addMember(playerId))
    }

final class TournamentApplicationService(
    tournamentRepository: TournamentRepository,
    playerRepository: PlayerRepository,
    clubRepository: ClubRepository,
    tableRepository: TableRepository,
    seatingPolicy: SeatingPolicy,
    transactionManager: TransactionManager = NoOpTransactionManager
):
  def createTournament(
      name: String,
      organizer: String,
      startsAt: Instant,
      endsAt: Instant,
      stages: Vector[TournamentStage]
  ): Tournament =
    transactionManager.inTransaction {
      val tournament = tournamentRepository.findByNameAndOrganizer(name, organizer) match
        case Some(existing) =>
          existing.copy(
            startsAt = startsAt,
            endsAt = endsAt,
            stages = stages.sortBy(_.order)
          )
        case None =>
          Tournament(
            id = IdGenerator.tournamentId(),
            name = name,
            organizer = organizer,
            startsAt = startsAt,
            endsAt = endsAt,
            stages = stages.sortBy(_.order)
          )

      tournamentRepository.save(tournament)
    }

  def registerPlayer(tournamentId: TournamentId, playerId: PlayerId): Option[Tournament] =
    transactionManager.inTransaction {
      tournamentRepository.findById(tournamentId).map { tournament =>
        tournamentRepository.save(tournament.registerPlayer(playerId))
      }
    }

  def registerClub(tournamentId: TournamentId, clubId: ClubId): Option[Tournament] =
    transactionManager.inTransaction {
      tournamentRepository.findById(tournamentId).map { tournament =>
        tournamentRepository.save(tournament.registerClub(clubId))
      }
    }

  def publishTournament(tournamentId: TournamentId): Option[Tournament] =
    transactionManager.inTransaction {
      tournamentRepository.findById(tournamentId).map { tournament =>
        tournamentRepository.save(tournament.publish)
      }
    }

  def startTournament(tournamentId: TournamentId): Option[Tournament] =
    transactionManager.inTransaction {
      tournamentRepository.findById(tournamentId).map { tournament =>
        tournamentRepository.save(tournament.start)
      }
    }

  def scheduleStageTables(
      tournamentId: TournamentId,
      stageId: TournamentStageId
  ): Vector[Table] =
    transactionManager.inTransaction {
      val existingTables = tableRepository.findByTournamentAndStage(tournamentId, stageId)
      if existingTables.nonEmpty then existingTables
      else
        val tournament = tournamentRepository
          .findById(tournamentId)
          .getOrElse(throw IllegalArgumentException(s"Tournament ${tournamentId.value} was not found"))

        val stage = tournament.stages
          .find(_.id == stageId)
          .getOrElse(throw IllegalArgumentException(s"Stage ${stageId.value} was not found"))

        val tournamentPlayers = resolveParticipants(tournament)
        val plannedTables = seatingPolicy.assignTables(tournamentPlayers, stage)

        tournamentRepository.save(tournament.activateStage(stageId).markScheduled)

        plannedTables.map { planned =>
          tableRepository.save(
            Table(
              id = IdGenerator.tableId(),
              tableNo = planned.tableNo,
              tournamentId = tournamentId,
              stageId = stageId,
              seats = planned.seats
            )
          )
        }
    }

  private def resolveParticipants(tournament: Tournament): Vector[Player] =
    val clubMembers = tournament.participatingClubs.flatMap { clubId =>
      clubRepository.findById(clubId).toVector.flatMap(_.members)
    }

    val playerIds = (tournament.participatingPlayers ++ clubMembers).distinct

    playerIds.flatMap { playerId =>
      playerRepository.findById(playerId)
    }

final class TableLifecycleService(
    tableRepository: TableRepository,
    paifuRepository: PaifuRepository,
    eventBus: DomainEventBus,
    transactionManager: TransactionManager = NoOpTransactionManager
):
  def startTable(tableId: TableId, startedAt: Instant = Instant.now()): Option[Table] =
    transactionManager.inTransaction {
      tableRepository.findById(tableId).map { table =>
        tableRepository.save(table.start(startedAt))
      }
    }

  def recordCompletedTable(tableId: TableId, paifu: Paifu): Option[Table] =
    transactionManager.inTransaction {
      tableRepository.findById(tableId).map { table =>
        validatePaifu(table, paifu)

        paifuRepository.save(paifu)
        val finishedTable = tableRepository.save(
          table.finish(paifu.id, paifu.metadata.recordedAt)
        )

        eventBus.publish(
          TableResultRecorded(
            tableId = table.id,
            tournamentId = table.tournamentId,
            stageId = table.stageId,
            paifu = paifu,
            occurredAt = paifu.metadata.recordedAt
          )
        )

        finishedTable
      }
    }

  private def validatePaifu(table: Table, paifu: Paifu): Unit =
    require(paifu.metadata.tableId == table.id, "Paifu table id does not match the table")
    require(
      paifu.metadata.tournamentId == table.tournamentId,
      "Paifu tournament id does not match the table"
    )
    require(paifu.metadata.stageId == table.stageId, "Paifu stage id does not match the table")
    require(
      paifu.metadata.seats.toSet == table.seats.toSet,
      "Paifu seat map does not match the scheduled table"
    )

final class RatingProjectionSubscriber(
    playerRepository: PlayerRepository,
    ratingService: RatingService
) extends DomainEventSubscriber:
  override def handle(event: DomainEvent): Unit =
    event match
      case TableResultRecorded(_, _, _, paifu, _) =>
        val players = paifu.finalStandings.flatMap { standing =>
          playerRepository.findById(standing.playerId)
        }

        val deltas = ratingService.calculateDeltas(players, paifu.finalStandings)

        deltas.foreach { delta =>
          playerRepository.findById(delta.playerId).foreach { player =>
            playerRepository.save(player.applyElo(delta.delta))
          }
        }

final class ClubProjectionSubscriber(
    clubRepository: ClubRepository,
    playerRepository: PlayerRepository
) extends DomainEventSubscriber:
  override def handle(event: DomainEvent): Unit =
    event match
      case TableResultRecorded(_, _, _, paifu, _) =>
        paifu.finalStandings.foreach { standing =>
          playerRepository.findById(standing.playerId).flatMap(_.clubId).foreach { clubId =>
            clubRepository.findById(clubId).foreach { club =>
              val tableContribution = standing.finalPoints - 25000
              clubRepository.save(club.addPoints(tableContribution))
            }
          }
        }

final class DashboardProjectionSubscriber(
    paifuRepository: PaifuRepository,
    playerRepository: PlayerRepository,
    clubRepository: ClubRepository,
    dashboardRepository: DashboardRepository
) extends DomainEventSubscriber:
  override def handle(event: DomainEvent): Unit =
    event match
      case TableResultRecorded(_, _, _, paifu, occurredAt) =>
        val impactedPlayers = paifu.playerIds.distinct

        impactedPlayers.foreach { playerId =>
          val playerDashboard = buildPlayerDashboard(playerId, occurredAt)
          dashboardRepository.save(playerDashboard)
        }

        impactedPlayers
          .flatMap(playerId => playerRepository.findById(playerId).flatMap(_.clubId))
          .distinct
          .foreach { clubId =>
            clubRepository.findById(clubId).foreach { club =>
              dashboardRepository.save(buildClubDashboard(club, occurredAt))
            }
          }

  private def buildPlayerDashboard(playerId: PlayerId, at: Instant): Dashboard =
    val paifus = paifuRepository.findByPlayer(playerId)
    val rounds = paifus.flatMap(_.rounds)
    val winPointSamples = rounds.flatMap { round =>
      if round.result.winner.contains(playerId) then
        round.result.scoreChanges.find(_.playerId == playerId).map(_.delta)
      else None
    }
    val riichiCount = rounds.flatMap(_.actions).count { action =>
      action.actor.contains(playerId) && action.actionType == PaifuActionType.Riichi
    }
    val dealInCount = rounds.count { round =>
      round.result.outcome == HandOutcome.Ron && round.result.target.contains(playerId)
    }
    val winCount = rounds.count(_.result.winner.contains(playerId))
    val shantenTrajectory = rounds
      .flatMap(_.actions)
      .flatMap { action =>
        if action.actor.contains(playerId) then action.shantenAfterAction.map(_.toDouble)
        else None
      }

    Dashboard(
      owner = DashboardOwner.Player(playerId),
      sampleSize = rounds.size,
      dealInRate = ratio(dealInCount, rounds.size),
      winRate = ratio(winCount, rounds.size),
      averageWinPoints = average(winPointSamples.map(_.toDouble)),
      riichiRate = ratio(riichiCount, rounds.size),
      shantenTrajectory = shantenTrajectory.map(round2),
      lastUpdatedAt = at
    )

  private def buildClubDashboard(club: Club, at: Instant): Dashboard =
    val memberDashboards = club.members.flatMap { playerId =>
      dashboardRepository.findByOwner(DashboardOwner.Player(playerId))
    }

    val totalSamples = memberDashboards.map(_.sampleSize).sum

    Dashboard(
      owner = DashboardOwner.Club(club.id),
      sampleSize = totalSamples,
      dealInRate = weightedAverage(memberDashboards, _.dealInRate),
      winRate = weightedAverage(memberDashboards, _.winRate),
      averageWinPoints = weightedAverage(memberDashboards, _.averageWinPoints),
      riichiRate = weightedAverage(memberDashboards, _.riichiRate),
      shantenTrajectory = averageTrajectory(memberDashboards.map(_.shantenTrajectory)),
      lastUpdatedAt = at
    )

  private def ratio(numerator: Int, denominator: Int): Double =
    if denominator <= 0 then 0.0
    else round2(numerator.toDouble / denominator.toDouble)

  private def average(values: Vector[Double]): Double =
    if values.isEmpty then 0.0
    else round2(values.sum / values.size.toDouble)

  private def weightedAverage(
      dashboards: Vector[Dashboard],
      selector: Dashboard => Double
  ): Double =
    val totalWeight = dashboards.map(_.sampleSize).sum
    if totalWeight <= 0 then 0.0
    else
      round2(
        dashboards.map(dashboard => selector(dashboard) * dashboard.sampleSize).sum /
          totalWeight.toDouble
      )

  private def averageTrajectory(trajectories: Vector[Vector[Double]]): Vector[Double] =
    val maxLength = trajectories.map(_.size).foldLeft(0)(math.max)

    (0 until maxLength).toVector.flatMap { index =>
      val samples = trajectories.flatMap(_.lift(index))
      if samples.isEmpty then None
      else Some(round2(samples.sum / samples.size.toDouble))
    }

  private def round2(value: Double): Double =
    BigDecimal(value).setScale(2, BigDecimal.RoundingMode.HALF_UP).toDouble
