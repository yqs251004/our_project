package riichinexus.system.realtime.domain

import java.util.UUID
import scala.collection.concurrent.TrieMap

import cats.effect.{IO, Resource}
import cats.effect.std.Queue
import fs2.Stream
import riichinexus.system.realtime.objects.RealtimeEvent

/** 进程内实时事件广播总线。
  *
  * 每个订阅者拥有独立队列，发布事件时会复制到当前所有订阅者，供 SSE 或前端实时刷新通道消费。
  */
final class RealtimeEventBus:

  private val subscribers = TrieMap.empty[String, Queue[IO, RealtimeEvent]]

  def publish(event: RealtimeEvent): IO[Unit] =
    IO.defer {
      subscribers.values.toVector.foldLeft(IO.unit) { (acc, subscriber) =>
        acc.flatMap(_ => subscriber.offer(event))
      }
    }

  def subscribe: Resource[IO, Stream[IO, RealtimeEvent]] =
    Resource
      .make {
        for
          queue <- Queue.unbounded[IO, RealtimeEvent]
          subscriberId <- IO(UUID.randomUUID().toString)
          _ <- IO(subscribers.put(subscriberId, queue))
        yield subscriberId -> queue
      } { case (subscriberId, _) =>
        IO(subscribers.remove(subscriberId)).map(_ => ())
      }
      .map { case (_, queue) =>
        Stream.fromQueueUnterminated(queue)
      }

object RealtimeEventBus:

  def empty: RealtimeEventBus = new RealtimeEventBus
