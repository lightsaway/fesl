package fesl

import java.util.UUID

import cats.MonadError
import cats.effect.{Effect, Sync}
import cats.implicits._
import cats.effect.concurrent.Ref
import fesl.ExtractUUID.ExtractUUID
import fs2.Stream

class InMemoryLog[F[_]: Sync: Effect, E](storage: Ref[F, Map[UUID, Seq[E]]])(
    implicit extractor: ExtractUUID[E])
    extends LogTable[F, E] {

  override def insert(e: E): F[E] =
    for {
      s <- storage.get
      id = extractor(e)
      es = s.getOrElse(id, Seq[E]().empty) :+ e
      _ <- storage.update(s => s + (id -> es))
    } yield e

  override def select(id: UUID): fs2.Stream[F, E] =
    Stream
      .eval(for {
        s <- storage.get
        es = s.getOrElse(id, Seq[E]().empty)
      } yield es)
      .flatMap(Stream.emits(_))
}

object InMemoryLog {
  def createTable[F[_]: Sync, E]: F[Ref[F, Map[UUID, Seq[E]]]] =
    Ref.of(Map[UUID, Seq[E]]().empty)

  def apply[F[_], E](initial: Map[UUID, Seq[E]] = Map[UUID, Seq[E]]().empty)(
      implicit extractor: ExtractUUID[E],
      F: Effect[F],
      M: MonadError[F, Throwable]): F[InMemoryLog[F, E]] =
    Ref.of[F, Map[UUID, Seq[E]]](initial).map(new InMemoryLog[F, E](_))
}

class InMemoryView[F[_]: Sync: Effect, E](storage: Ref[F, Map[UUID, E]])(
    implicit extractor: ExtractUUID[E],
    M: MonadError[F, Throwable])
    extends ViewTable[F, E] {

  override def insert(e: E): F[E] =
    storage.update(s => s + (extractor(e) -> e)).as(e)
  override def select(id: UUID): F[Option[E]] = storage.get.map(_.get(id))

}

object InMemoryView {
  def createTable[F[_]: Sync, E]: F[Ref[F, Map[UUID, E]]] =
    Ref.of(Map[UUID, E]().empty)

  def apply[F[_], E](initial: Map[UUID, E] = Map[UUID, E]().empty)(
      implicit extractor: ExtractUUID[E],
      F: Effect[F],
      M: MonadError[F, Throwable]): F[InMemoryView[F, E]] =
    Ref.of[F, Map[UUID, E]](initial).map(new InMemoryView[F, E](_))
}
