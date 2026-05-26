package riichinexus.infrastructure.memory

import riichinexus.application.ports.*
import riichinexus.domain.model.*
import riichinexus.microservices.auth.objects.{AccountCredential, AuthenticatedSession}

private object InMemoryRepositoryLockSupport:
  def nextVersion(
      aggregateType: String,
      aggregateId: String,
      incomingVersion: Int,
      currentVersion: Option[Int]
  ): Int =
    currentVersion match
      case None =>
        if incomingVersion != 0 then
          throw OptimisticConcurrencyException(aggregateType, aggregateId, incomingVersion, None)
        1
      case Some(actual) =>
        if actual != incomingVersion then
          throw OptimisticConcurrencyException(aggregateType, aggregateId, incomingVersion, Some(actual))
        actual + 1

final class InMemoryGuestSessionRepository extends GuestSessionRepository:
  private val state = InMemoryKeyValueStore[GuestSessionId, GuestAccessSession]()

  override def save(session: GuestAccessSession): GuestAccessSession =
    val persisted = session.copy(
      version = InMemoryRepositoryLockSupport.nextVersion(
        "guest-session",
        session.id.value,
        session.version,
        state.get(session.id).map(_.version)
      )
    )
    state.upsert(persisted.id, persisted)
    persisted

  override def findById(id: GuestSessionId): Option[GuestAccessSession] =
    state.get(id)

  override def findAll(): Vector[GuestAccessSession] =
    state.values

object InMemoryGuestSessionRepository:
  def apply(): InMemoryGuestSessionRepository =
    new InMemoryGuestSessionRepository()

final class InMemoryAccountCredentialRepository extends AccountCredentialRepository:
  private val state = InMemoryKeyValueStore[String, AccountCredential]()

  override def save(credential: AccountCredential): AccountCredential =
    val persisted = credential.copy(
      version = InMemoryRepositoryLockSupport.nextVersion(
        "account-credential",
        credential.username,
        credential.version,
        state.get(credential.username).map(_.version)
      )
    )
    state.upsert(persisted.username, persisted)
    persisted

  override def findByUsername(username: String): Option[AccountCredential] =
    state.get(AccountCredential.normalizeUsername(username))

  override def findByPlayerId(playerId: PlayerId): Option[AccountCredential] =
    state.values.find(_.playerId == playerId)

  override def findAll(): Vector[AccountCredential] =
    state.values

object InMemoryAccountCredentialRepository:
  def apply(): InMemoryAccountCredentialRepository =
    new InMemoryAccountCredentialRepository()

final class InMemoryAuthenticatedSessionRepository extends AuthenticatedSessionRepository:
  private val state = InMemoryKeyValueStore[String, AuthenticatedSession]()

  override def save(session: AuthenticatedSession): AuthenticatedSession =
    val persisted = session.copy(
      version = InMemoryRepositoryLockSupport.nextVersion(
        "authenticated-session",
        session.token,
        session.version,
        state.get(session.token).map(_.version)
      )
    )
    state.upsert(persisted.token, persisted)
    persisted

  override def findByToken(token: String): Option[AuthenticatedSession] =
    state.get(token)

  override def findAll(): Vector[AuthenticatedSession] =
    state.values

object InMemoryAuthenticatedSessionRepository:
  def apply(): InMemoryAuthenticatedSessionRepository =
    new InMemoryAuthenticatedSessionRepository()

final class InMemoryMatchRecordRepository extends MatchRecordRepository:
  private val state = InMemoryKeyValueStore[MatchRecordId, MatchRecord]()

  override def save(record: MatchRecord): MatchRecord =
    state.upsert(record.id, record)

  override def findById(id: MatchRecordId): Option[MatchRecord] =
    state.get(id)

  override def findByTable(tableId: TableId): Option[MatchRecord] =
    state.values.find(_.tableId == tableId)

  override def findAll(): Vector[MatchRecord] =
    state.values

object InMemoryMatchRecordRepository:
  def apply(): InMemoryMatchRecordRepository =
    new InMemoryMatchRecordRepository()

final class InMemoryPaifuRepository extends PaifuRepository:
  private val state = InMemoryKeyValueStore[PaifuId, Paifu]()

  override def save(paifu: Paifu): Paifu =
    state.upsert(paifu.id, paifu)

  override def findById(id: PaifuId): Option[Paifu] =
    state.get(id)

  override def findAll(): Vector[Paifu] =
    state.values

object InMemoryPaifuRepository:
  def apply(): InMemoryPaifuRepository =
    new InMemoryPaifuRepository()

final class InMemoryEventCascadeRecordRepository extends EventCascadeRecordRepository:
  private val state = InMemoryKeyValueStore[EventCascadeRecordId, EventCascadeRecord]()

  override def save(record: EventCascadeRecord): EventCascadeRecord =
    val persisted = record.copy(
      version = InMemoryRepositoryLockSupport.nextVersion(
        "event-cascade-record",
        record.id.value,
        record.version,
        state.get(record.id).map(_.version)
      )
    )
    state.upsert(persisted.id, persisted)
    persisted

  override def findById(id: EventCascadeRecordId): Option[EventCascadeRecord] =
    state.get(id)

  override def findAll(): Vector[EventCascadeRecord] =
    state.values.sortBy(record => (record.occurredAt, record.id.value))

object InMemoryEventCascadeRecordRepository:
  def apply(): InMemoryEventCascadeRecordRepository =
    new InMemoryEventCascadeRecordRepository()
