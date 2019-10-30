package fesl
import java.time.LocalDateTime
import java.util.UUID

import cats.effect.Bracket
import skunk._
import skunk.implicits._
import skunk.codec.all._
import fs2.Stream.resource
import skunk.data.Type
import cats.implicits._
import fesl.ExtractUUID.ExtractUUID
import fesl.PgLog.LogEncoder
import fesl.types.BracketThrow

import scala.util.Try

object codecs {
  val uuid: Codec[UUID] = Codec.simple(_.toString,
                                       x => Try(UUID.fromString(x)).toEither.left.map(_.getMessage),
                                       Type.uuid)
  val json: Codec[String] = ???
}
import codecs._

object types {
  type BracketThrow[F[_]] = Bracket[F, Throwable]
}
object PgView {
  def createTable[F[_]: BracketThrow](implicit s: Session[F]): F[Unit] = {
    val cmd: Command[Void] = sql"""
                                   CREATE TABLE IF NOT EXISTS aggregate_view (
                                                      id UUID PRIMARY KEY,
                                                      state TEXT,
                                                      updated_on TIMESTAMP WITH TIME ZONE
                                )
                     """.command
    s.execute(cmd).void
  }
}

class PgView[F[_]: BracketThrow, E](implicit s: Session[F],
                                    codec: Codec[E],
                                    extractUUID: ExtractUUID[E])
    extends ViewTable[F, E] {

  val selectQ: Query[UUID, E] =
    sql"""
      select * from aggregate_view
      WHERE id =  $uuid
    """.query(codec)

  val insertQ: Command[E] =
    sql"""
          INSERT INTO aggregate_view
          VALUES ($codec)
          ON CONFLICT (id)
          DO UPDATE SET state = Excluded.state, datetime = Excluded.state;
       """.command

  override def insert(e: E): F[E] =
    s.prepare(insertQ).use(_.execute(e)).as(e)

  override def select(id: UUID): F[Option[E]] =
    s.prepare(selectQ).use(_.option(id))
}

object PgLog {
  type LogRow        = java.util.UUID ~ Long ~ String ~ String ~ LocalDateTime
  type LogEncoder[E] = E => LogRow

  def decoder[E](f: LogRow => E) = (uuid ~ int8 ~ text ~ text ~ timestamp).map(f)
  def deriveCodec[E]             = (uuid ~ int8 ~ text ~ text ~ timestamp).map(???)

  def createTable[F[_]: BracketThrow](implicit s: Session[F]): F[Unit] = {
    val cmd: Command[Void] = sql"""
                                   CREATE TABLE IF NOT EXISTS event_log (
                                                      id UUID PRIMARY KEY,
                                                      agg_id UUID,
                                                      seq_nr INTEGER
                                                      e_type VARCHAR(50),
                                                      event TEXT,
                                                      appeared_on TIMESTAMP WITH TIME ZONE
                                )
                     """.command
    s.execute(cmd).void
  }
}

class PgLog[F[_]: BracketThrow, E](implicit s: Session[F],
                                   codec: Codec[E],
                                   extractUUID: ExtractUUID[E])
    extends LogTable[F, E] {

  val select: Query[UUID, E] =
    sql"""
      select * from event_log
      WHERE agg_id =  $uuid
    """.query(codec)

  val insert: Command[E] =
    sql"""
         INSERT INTO log
         VALUES ($codec)
       """.command

  override def insert(e: E): F[E] =
    s.prepare(insert).use(_.execute(e)).as(e)

  override def select(id: UUID): fs2.Stream[F, E] =
    for {
      pq     <- resource(s.prepare(select))
      events <- pq.stream(id, 20)
    } yield events

}
