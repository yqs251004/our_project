package riichinexus.application.service

import java.time.Instant

import scala.collection.mutable

import riichinexus.application.ports.*
import riichinexus.domain.model.*
import upickle.default.*

private given ReadWriter[Instant] =
  readwriter[String].bimap[Instant](_.toString, Instant.parse)

final case class PerformanceStatusCount(
    statusCode: Int,
    count: Long
) derives CanEqual

object PerformanceStatusCount:
  given ReadWriter[PerformanceStatusCount] = macroRW

final case class PerformanceMetricEntry(
    key: String,
    count: Long,
    totalMillis: Double,
    averageMillis: Double,
    maxMillis: Double,
    lastMillis: Double,
    lastUpdatedAt: Instant,
    statusCounts: Vector[PerformanceStatusCount] = Vector.empty
) derives CanEqual

object PerformanceMetricEntry:
  given ReadWriter[PerformanceMetricEntry] = macroRW

final case class PerformanceDiagnosticsSnapshot(
    startedAt: Instant,
    generatedAt: Instant,
    totalRequestCount: Long,
    totalRepositoryCallCount: Long,
    slowestRequests: Vector[PerformanceMetricEntry],
    busiestRequests: Vector[PerformanceMetricEntry],
    slowestRepositoryCalls: Vector[PerformanceMetricEntry],
    busiestRepositoryCalls: Vector[PerformanceMetricEntry]
) derives CanEqual

object PerformanceDiagnosticsSnapshot:
  given ReadWriter[PerformanceDiagnosticsSnapshot] = macroRW

final class PerformanceDiagnosticsService(
    val startedAt: Instant = Instant.now()
):
  private final class MetricAccumulator:
    var count: Long = 0L
    var totalNanos: Long = 0L
    var maxNanos: Long = 0L
    var lastNanos: Long = 0L
    var lastUpdatedAt: Instant = startedAt
    val statusCounts: mutable.Map[Int, Long] = mutable.Map.empty

    def record(durationNanos: Long, occurredAt: Instant, statusCode: Option[Int]): Unit =
      count += 1
      totalNanos += durationNanos
      maxNanos = math.max(maxNanos, durationNanos)
      lastNanos = durationNanos
      lastUpdatedAt = occurredAt
      statusCode.foreach(code =>
        statusCounts.update(code, statusCounts.getOrElse(code, 0L) + 1L)
      )

  private val requestMetrics = mutable.Map.empty[String, MetricAccumulator]
  private val repositoryMetrics = mutable.Map.empty[String, MetricAccumulator]

  def recordRequest(
      method: String,
      path: String,
      statusCode: Int,
      durationNanos: Long,
      occurredAt: Instant = Instant.now()
  ): Unit = synchronized {
    requestMetrics
      .getOrElseUpdate(s"$method $path", MetricAccumulator())
      .record(durationNanos, occurredAt, Some(statusCode))
  }

  def recordRepositoryCall(
      repository: String,
      operation: String,
      durationNanos: Long,
      occurredAt: Instant = Instant.now()
  ): Unit = synchronized {
    repositoryMetrics
      .getOrElseUpdate(s"$repository.$operation", MetricAccumulator())
      .record(durationNanos, occurredAt, None)
  }

  def snapshot(
      limit: Int = 15,
      generatedAt: Instant = Instant.now()
  ): PerformanceDiagnosticsSnapshot = synchronized {
    val safeLimit = math.max(1, limit)
    val requestEntries = requestMetrics.toVector.map(toEntry)
    val repositoryEntries = repositoryMetrics.toVector.map(toEntry)

    PerformanceDiagnosticsSnapshot(
      startedAt = startedAt,
      generatedAt = generatedAt,
      totalRequestCount = requestEntries.map(_.count).sum,
      totalRepositoryCallCount = repositoryEntries.map(_.count).sum,
      slowestRequests = requestEntries.sortBy(entry => (-entry.averageMillis, -entry.maxMillis, entry.key)).take(safeLimit),
      busiestRequests = requestEntries.sortBy(entry => (-entry.totalMillis, -entry.count, entry.key)).take(safeLimit),
      slowestRepositoryCalls = repositoryEntries.sortBy(entry => (-entry.averageMillis, -entry.maxMillis, entry.key)).take(safeLimit),
      busiestRepositoryCalls = repositoryEntries.sortBy(entry => (-entry.totalMillis, -entry.count, entry.key)).take(safeLimit)
    )
  }

  private def toEntry(raw: (String, MetricAccumulator)): PerformanceMetricEntry =
    val (key, metric) = raw
    val nanosPerMillis = 1000000.0
    PerformanceMetricEntry(
      key = key,
      count = metric.count,
      totalMillis = metric.totalNanos / nanosPerMillis,
      averageMillis =
        if metric.count <= 0 then 0.0
        else metric.totalNanos.toDouble / metric.count.toDouble / nanosPerMillis,
      maxMillis = metric.maxNanos / nanosPerMillis,
      lastMillis = metric.lastNanos / nanosPerMillis,
      lastUpdatedAt = metric.lastUpdatedAt,
      statusCounts = metric.statusCounts.toVector
        .sortBy(_._1)
        .map { case (statusCode, count) =>
          PerformanceStatusCount(statusCode = statusCode, count = count)
        }
    )

private trait RepositoryInstrumentationSupport:
  protected def diagnostics: PerformanceDiagnosticsService
  protected def repositoryName: String

  protected def instrument[A](operation: String)(body: => A): A =
    val startedAt = System.nanoTime()
    try body
    finally
      diagnostics.recordRepositoryCall(
        repository = repositoryName,
        operation = operation,
        durationNanos = System.nanoTime() - startedAt
      )

final class InstrumentedPlayerRepository(
    delegate: PlayerRepository,
    protected val diagnostics: PerformanceDiagnosticsService
) extends PlayerRepository
    with RepositoryInstrumentationSupport:
  protected val repositoryName = "PlayerRepository"

  def save(player: Player): Player = instrument("save")(delegate.save(player))
  def findById(id: PlayerId): Option[Player] = instrument("findById")(delegate.findById(id))
  def findByUserId(userId: String): Option[Player] = instrument("findByUserId")(delegate.findByUserId(userId))
  def findAll(): Vector[Player] = instrument("findAll")(delegate.findAll())
  override def findByIds(ids: Vector[PlayerId]): Vector[Player] = instrument("findByIds")(delegate.findByIds(ids))
  override def findByClub(clubId: ClubId): Vector[Player] = instrument("findByClub")(delegate.findByClub(clubId))

final class InstrumentedClubRepository(
    delegate: ClubRepository,
    protected val diagnostics: PerformanceDiagnosticsService
) extends ClubRepository
    with RepositoryInstrumentationSupport:
  protected val repositoryName = "ClubRepository"

  def save(club: Club): Club = instrument("save")(delegate.save(club))
  def findById(id: ClubId): Option[Club] = instrument("findById")(delegate.findById(id))
  def findByName(name: String): Option[Club] = instrument("findByName")(delegate.findByName(name))
  def findAll(): Vector[Club] = instrument("findAll")(delegate.findAll())
  override def findByIds(ids: Vector[ClubId]): Vector[Club] = instrument("findByIds")(delegate.findByIds(ids))
  override def findFiltered(
      activeOnly: Boolean,
      joinableOnly: Boolean,
      memberId: Option[PlayerId],
      adminId: Option[PlayerId],
      name: Option[String]
  ): Vector[Club] =
    instrument("findFiltered")(delegate.findFiltered(activeOnly, joinableOnly, memberId, adminId, name))
  override def findActive(): Vector[Club] = instrument("findActive")(delegate.findActive())

final class InstrumentedTournamentRepository(
    delegate: TournamentRepository,
    protected val diagnostics: PerformanceDiagnosticsService
) extends TournamentRepository
    with RepositoryInstrumentationSupport:
  protected val repositoryName = "TournamentRepository"

  def save(tournament: Tournament): Tournament = instrument("save")(delegate.save(tournament))
  def findById(id: TournamentId): Option[Tournament] = instrument("findById")(delegate.findById(id))
  def findByNameAndOrganizer(name: String, organizer: String): Option[Tournament] =
    instrument("findByNameAndOrganizer")(delegate.findByNameAndOrganizer(name, organizer))
  def findAll(): Vector[Tournament] = instrument("findAll")(delegate.findAll())
  override def findByIds(ids: Vector[TournamentId]): Vector[Tournament] = instrument("findByIds")(delegate.findByIds(ids))
  override def findFiltered(
      status: Option[TournamentStatus],
      adminId: Option[PlayerId],
      organizer: Option[String],
      includeDraft: Boolean
  ): Vector[Tournament] =
    instrument("findFiltered")(delegate.findFiltered(status, adminId, organizer, includeDraft))
  override def findByClub(clubId: ClubId): Vector[Tournament] = instrument("findByClub")(delegate.findByClub(clubId))
  override def findPublic(): Vector[Tournament] = instrument("findPublic")(delegate.findPublic())
  override def findByAdmin(playerId: PlayerId): Vector[Tournament] = instrument("findByAdmin")(delegate.findByAdmin(playerId))

final class InstrumentedTableRepository(
    delegate: TableRepository,
    protected val diagnostics: PerformanceDiagnosticsService
) extends TableRepository
    with RepositoryInstrumentationSupport:
  protected val repositoryName = "TableRepository"

  def save(table: Table): Table = instrument("save")(delegate.save(table))
  def delete(id: TableId): Unit = instrument("delete")(delegate.delete(id))
  def findById(id: TableId): Option[Table] = instrument("findById")(delegate.findById(id))
  def findByTournamentAndStage(
      tournamentId: TournamentId,
      stageId: TournamentStageId
  ): Vector[Table] =
    instrument("findByTournamentAndStage")(delegate.findByTournamentAndStage(tournamentId, stageId))
  def findAll(): Vector[Table] = instrument("findAll")(delegate.findAll())
  override def findByTournamentIds(tournamentIds: Vector[TournamentId]): Vector[Table] =
    instrument("findByTournamentIds")(delegate.findByTournamentIds(tournamentIds))
  override def findByStatus(status: TableStatus): Vector[Table] =
    instrument("findByStatus")(delegate.findByStatus(status))

final class InstrumentedMatchRecordRepository(
    delegate: MatchRecordRepository,
    protected val diagnostics: PerformanceDiagnosticsService
) extends MatchRecordRepository
    with RepositoryInstrumentationSupport:
  protected val repositoryName = "MatchRecordRepository"

  def save(record: MatchRecord): MatchRecord = instrument("save")(delegate.save(record))
  def findById(id: MatchRecordId): Option[MatchRecord] = instrument("findById")(delegate.findById(id))
  def findByTable(tableId: TableId): Option[MatchRecord] = instrument("findByTable")(delegate.findByTable(tableId))
  def findAll(): Vector[MatchRecord] = instrument("findAll")(delegate.findAll())
  override def findByTournamentAndStage(
      tournamentId: TournamentId,
      stageId: TournamentStageId
  ): Vector[MatchRecord] =
    instrument("findByTournamentAndStage")(delegate.findByTournamentAndStage(tournamentId, stageId))
  override def findRecentByClub(clubId: ClubId, limit: Int): Vector[MatchRecord] =
    instrument("findRecentByClub")(delegate.findRecentByClub(clubId, limit))
  override def findByPlayer(playerId: PlayerId): Vector[MatchRecord] =
    instrument("findByPlayer")(delegate.findByPlayer(playerId))

final class InstrumentedGlobalDictionaryRepository(
    delegate: GlobalDictionaryRepository,
    protected val diagnostics: PerformanceDiagnosticsService
) extends GlobalDictionaryRepository
    with RepositoryInstrumentationSupport:
  protected val repositoryName = "GlobalDictionaryRepository"

  def save(entry: GlobalDictionaryEntry): GlobalDictionaryEntry = instrument("save")(delegate.save(entry))
  def findByKey(key: String): Option[GlobalDictionaryEntry] = instrument("findByKey")(delegate.findByKey(key))
  def findAll(): Vector[GlobalDictionaryEntry] = instrument("findAll")(delegate.findAll())
