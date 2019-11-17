package fesl.skunk

import java.time.LocalDateTime
import java.util.UUID

import cats.implicits._
import fesl.core.ExtractUUID.ExtractUUID
import fesl.core.LogTable
import fs2.Stream.resource
import skunk.{Codec, Command, Query, Session, Void, ~}
import skunk.codec.all._
import fesl.skunk.codecs._
import skunk.implicits._
import fesl.skunk.types.BracketThrow
import cats.implicits._

object PgLog {
  type LogRow        = java.util.UUID ~ Long ~ String ~ String ~ LocalDateTime
  type LogEncoder[E] = E => LogRow

  def decoder[E](f: LogRow => E) = (uuid ~ int8 ~ text ~ text ~ timestamp).map(f)
  def deriveCodec[E]             = (uuid ~ int8 ~ text ~ text ~ timestamp).map(???)

  def createTable[F[_]: BracketThrow](implicit s: Session[F]): F[Unit] = {
    val cmd: Command[Void] = sql"""
                                   CREATE TABLE IF NOT EXISTS event_log (
                                                      id UUID,
                                                      agg_id UUID,
                                                      e_type VARCHAR(50),
                                                      event JSON,
                                                      appeared_on TIMESTAMP(6),
                                                      PRIMARY KEY(id, agg_id)
                                )
                     """.command
    s.execute(cmd).void
  }
}

class PgLog[F[_]: BracketThrow, E](implicit s: Session[F],
                                   codec: Codec[E],
                                   extractUUID: ExtractUUID[E])
    extends LogTable[F, E] {

  private val select: Query[UUID, E] =
    sql"""
      select * from event_log
      WHERE agg_id =  $uuid
    """.query(codec)

  private val insert: Command[E] =
    sql"""
         INSERT INTO event_log
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
