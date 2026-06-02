package riichinexus.system.memory

import java.util.concurrent.atomic.AtomicReference
import scala.annotation.tailrec

private[memory] enum RepositoryChange[+K, +V]:
  case Upsert(key: K, value: V)
  case Delete(key: K)
  case Append(value: V)

private[memory] final case class RepositoryState[K, V](
    items: Vector[(K, V)] = Vector.empty,
    changes: Vector[RepositoryChange[K, V]] = Vector.empty,
    nextSequenceNo: Long = 1L
):
  def get(key: K): Option[V] =
    items.find(_._1 == key).map(_._2)

  def values: Vector[V] =
    items.map(_._2)

  def upsert(key: K, value: V): RepositoryState[K, V] =
    val nextItems = items.indexWhere(_._1 == key) match
      case -1    => items :+ (key -> value)
      case index => items.updated(index, key -> value)
    copy(
      items = nextItems,
      changes = changes :+ RepositoryChange.Upsert(key, value)
    )

  def delete(key: K): RepositoryState[K, V] =
    copy(
      items = items.filterNot(_._1 == key),
      changes = changes :+ RepositoryChange.Delete(key)
    )

  def allocateSequenceNo: (RepositoryState[K, V], Long) =
    copy(nextSequenceNo = nextSequenceNo + 1L) -> nextSequenceNo

private[memory] object RepositoryState:
  def empty[K, V]: RepositoryState[K, V] =
    RepositoryState()

private[memory] final class InMemoryKeyValueStore[K, V](
    initialState: RepositoryState[K, V] = RepositoryState.empty[K, V]
):
  private val ref = AtomicReference(initialState)

  def get(key: K): Option[V] =
    ref.get().get(key)

  def values: Vector[V] =
    ref.get().values

  def changes: Vector[RepositoryChange[K, V]] =
    ref.get().changes

  def upsert(key: K, value: V): V =
    modify(state => state.upsert(key, value) -> value)

  def delete(key: K): Unit =
    modify(state => state.delete(key) -> ())

  @tailrec
  def modify[A](f: RepositoryState[K, V] => (RepositoryState[K, V], A)): A =
    val current = ref.get()
    val (next, result) = f(current)
    if ref.compareAndSet(current, next) then result
    else modify(f)

private[memory] final case class AppendOnlyState[V](
    items: Vector[V] = Vector.empty,
    changes: Vector[RepositoryChange[Nothing, V]] = Vector.empty
):
  def append(value: V): AppendOnlyState[V] =
    copy(
      items = items :+ value,
      changes = changes :+ RepositoryChange.Append(value)
    )

private[memory] final class InMemoryAppendOnlyStore[V](
    initialValues: Vector[V] = Vector.empty
):
  private val ref = AtomicReference(AppendOnlyState[V](items = initialValues))

  def values: Vector[V] =
    ref.get().items

  def changes: Vector[RepositoryChange[Nothing, V]] =
    ref.get().changes

  def append(value: V): V =
    modify(state => state.append(value) -> value)

  @tailrec
  private def modify[A](f: AppendOnlyState[V] => (AppendOnlyState[V], A)): A =
    val current = ref.get()
    val (next, result) = f(current)
    if ref.compareAndSet(current, next) then result
    else modify(f)
