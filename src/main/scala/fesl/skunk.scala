package fesl
import java.util.UUID

import skunk.{Codec, _}
import skunk.implicits._
import skunk.codec.all._
import fs2.Stream.resource
import fs2.Stream._
import skunk.data.{Completion, Type}
import cats.effect.implicits._
import fesl.ExtractUUID.ExtractUUID

import scala.util.Try

object codecs {
  val uuid: Codec[UUID] = Codec.simple(_.toString,
                                       x => Try(UUID.fromString(x)).toEither.left.map(_.getMessage),
                                       Type.uuid)
}
import codecs._

class PgView[F[_], E](implicit s: Session[F],
                      decoder: Decoder[E],
                      encoder: Codec[E],
                      extractUUID: ExtractUUID[E])
    extends ViewTable[F, E] {

  val selectQ: Query[UUID, E] =
    sql"""
      select id, seq_nr, event, hash, datetime
      from log
      WHERE id =  $uuid
    """.query(decoder)

  val insertQ: Command[E ~ UUID] =
    sql"""
         UPDATE  view
         SET state = $encoder
         WHERE id = $uuid
       """.command

  override def insert(e: E): F[E] =
    resource(s.prepare(insertQ)).map(_.execute(e ~ extractUUID(e))).compile.drain.as(e)

  override def select(id: UUID): F[Option[E]] =
    resource(s.prepare(selectQ)).map(_.option(id)).head.compile.toList
}
class PgLog[F[_], E](implicit s: Session[F], decoder: Decoder[E], encoder: Codec[E])
    extends LogTable[F, E] {

  val select: Query[UUID, E] =
    sql"""
      select id, seq_nr, event, hash, datetime
      from log
      WHERE id =  $uuid
    """.query(decoder)

  val insert: Command[E] =
    sql"""
         INSERT INTO log
         VALUES ($encoder)
       """.command

  override def insert(e: E): F[E] =
    resource(s.prepare(insert)).map(_.execute(e)).compile.drain.as(e)

  override def select(id: UUID): fs2.Stream[F, E] =
    for {
      pq     <- resource(s.prepare(select))
      events <- pq.stream(id, 20)
    } yield events

}
