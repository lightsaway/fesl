package fesl
import java.util.UUID

import cats.effect.Bracket
import skunk.{Codec, _}
import skunk.implicits._
import skunk.codec.all._
import fs2.Stream.resource
import skunk.data.Type
import cats.implicits._
import cats.effect.implicits._
import fesl.ExtractUUID.ExtractUUID
import fesl.PgLog.LogEncoder
import fesl.types.BracketThrow

import scala.util.Try

object codecs {
  val uuid: Codec[UUID] = Codec.simple(_.toString,
                                       x => Try(UUID.fromString(x)).toEither.left.map(_.getMessage),
                                       Type.uuid)
}
import codecs._

object types {
  type BracketThrow[F[_]] = Bracket[F, Throwable]
}

class PgView[F[_]: BracketThrow, E](implicit s: Session[F],
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

  val insertQ: Command[UUID ~ E] =
    sql"""
          INSERT INTO view
          VALUES ($uuid, $encoder, now())
          ON CONFLICT (id)
          DO UPDATE SET state = Excluded.state, datetime = Excluded.state;
       """.command

  override def insert(e: E): F[E] =
    s.prepare(insertQ).use(_.execute(extractUUID(e) ~ e)).as(e)

  override def select(id: UUID): F[Option[E]] =
    s.prepare(selectQ).use(_.option(id))
}

object PgLog {
  type LogEncoder[E] = E => java.util.UUID ~ Long ~ String ~ String
}

class PgLog[F[_]: BracketThrow, E](implicit s: Session[F],
                                   decoder: Decoder[E],
                                   encoder: LogEncoder[E],
                                   extractUUID: ExtractUUID[E])
    extends LogTable[F, E] {

  // table schema id, seq_nr, event, hash, datetime

  val select: Query[UUID, E] =
    sql"""
      select id, seq_nr, event, hash, datetime
      from log
      WHERE id =  $uuid
    """.query(decoder)

  val insert: Command[E] =
    sql"""
         INSERT INTO log
         VALUES ($uuid, $int8, $text, $text, now())
       """.command.contramap(encoder)

  override def insert(e: E): F[E] =
    s.prepare(insert).use(_.execute(e)).as(e)

  override def select(id: UUID): fs2.Stream[F, E] =
    for {
      pq     <- resource(s.prepare(select))
      events <- pq.stream(id, 20)
    } yield events

}
