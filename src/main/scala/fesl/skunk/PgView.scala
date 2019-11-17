package fesl.skunk

import java.util.UUID

import fesl.core.ExtractUUID.ExtractUUID
import fesl.core.ViewTable
import skunk.{Codec, Command, Query, Session, Void}
import fesl.skunk.codecs.uuid
import fesl.skunk.types.BracketThrow
import skunk.implicits._
import cats.implicits._

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
