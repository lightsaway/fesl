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
      _ <- storage.modify(s => (s + (id -> es), e))
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
}

class InMemoryView[F[_]: Sync: Effect, E](storage: Ref[F, Map[UUID, E]])(
    implicit extractor: ExtractUUID[E],
    M: MonadError[F, Throwable])
    extends ViewTable[F, E] {

  override def insert(e: E): F[E] =
    storage.modify(s => (s + (extractor(e) -> e), e))
  override def select(id: UUID): F[Option[E]] = storage.get.map(_.get(id))

}

object InMemoryView {
  def createTable[F[_]: Sync, E]: F[Ref[F, Map[UUID, E]]] =
    Ref.of(Map[UUID, E]().empty)
}
