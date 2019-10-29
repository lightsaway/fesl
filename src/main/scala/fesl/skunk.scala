package fesl
import java.util.UUID

import skunk._
import skunk.implicits._
import skunk.codec.all._
import fs2.Stream.resource
import skunk.data.{Completion, Type}
import cats.effect.implicits._

import scala.util.Try
class PgView[F[_], E](implicit s: Session[F], decoder: Decoder[E]) extends ViewTable[F, E] {
  override def insert(e: E): F[E]             = ???
  override def select(id: UUID): F[Option[E]] = ???
}

class PgLog[F[_], E](implicit s: Session[F], decoder: Decoder[E], encoder: Codec[E])
    extends LogTable[F, E] {
  val uuid: Codec[UUID] = Codec.simple(_.toString,
                                       x => Try(UUID.fromString(x)).toEither.left.map(_.getMessage),
                                       Type.uuid)

  val select: Query[UUID, E] =
    sql"""
      select id, seq_nr, event, hash, datetime
      from log
      WHERE id =  $uuid
    """.query(decoder)

  val insertCity: Command[E] =
    sql"""
         INSERT INTO log
         VALUES ($encoder)
       """.command

  override def insert(e: E): F[E] =
    resource(s.prepare(insertCity)).map(_.execute(e)).compile.drain.as(e)

  override def select(id: UUID): fs2.Stream[F, E] =
    for {
      pq     <- resource(s.prepare(select))
      events <- pq.stream(id, 20)
    } yield events

}
