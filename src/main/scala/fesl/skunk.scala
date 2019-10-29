package fesl
import java.util.UUID

import skunk._
import skunk.implicits._
import skunk.codec.all._
import fs2.Stream.resource
import skunk.data.Type

import scala.util.Try
class PgView[F[_], E] extends ViewTable[F, E] {
  override def insert(e: E): F[E]             = ???
  override def select(id: UUID): F[Option[E]] = ???
}

class PgLog[F[_], E](implicit s: Session[F], sqlDecoder: Decoder[E]) extends LogTable[F, E] {
  val varchar: Codec[UUID] = Codec.simple(
    _.toString,
    x => Try(UUID.fromString(x)).toEither.left.map(_.getMessage),
    Type.uuid)

  val select: Query[UUID, E] =
    sql"""
      select id, seq_nr, event, datetime
      from log
      WHERE id =  $varchar
    """.query(sqlDecoder)

  override def insert(e: E): F[E] = ???

  override def select(id: UUID): fs2.Stream[F, E] =
    for {
      pq     <- resource(s.prepare(select))
      events <- pq.stream(id, 20)
    } yield events
}
