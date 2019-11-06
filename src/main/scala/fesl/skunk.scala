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
import fesl.types.BracketThrow

import scala.util.Try

import io.circe.syntax._

object codecs {
  import io.circe.parser
  val uuid: Codec[UUID] =
    Codec.simple(_.toString, x => Try(UUID.fromString(x)).toEither.leftMap(_.getMessage), Type.uuid)

  def decocde[A](a: String)(implicit encoder: io.circe.Encoder[A], decoder: io.circe.Decoder[A]) =
    parser.decode[A](a).leftMap(_.getMessage())

  def json[A](implicit encoder: io.circe.Encoder[A], decoder: io.circe.Decoder[A]): Codec[A] =
    Codec.simple(_.asJson.noSpaces, x => decocde[A](x), Type.json)

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
                                                      state JSON,
                                                      updated_at TIMESTAMP(6)
                                )
                     """.command
    s.execute(cmd).void
  }
}

class PgView[F[_]: BracketThrow, E](implicit s: Session[F],
                                    codec: Codec[E],
                                    extractUUID: ExtractUUID[E])
    extends ViewTable[F, E] {

  private val selectQ: Query[UUID, E] =
    sql"""
      select * from aggregate_view
      WHERE id =  $uuid
    """.query(codec)

  private val insertQ: Command[E] =
    sql"""
          INSERT INTO aggregate_view
          VALUES ($codec)
          ON CONFLICT (id)
          DO UPDATE SET state = Excluded.state, updated_at = Excluded.updated_at;
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
                                   CREATE TABLE event_log (
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
