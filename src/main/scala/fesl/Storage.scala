package fesl

import java.util.UUID

import cats.effect.Effect
import cats.implicits._
import cats.kernel.Monoid

trait Extractor[E] {
  def extract: E => UUID
}

/**
  * fesl.Storage class that replays events and inserts events
  * @param LOG - requires instance of [[LogTable]]
  * @param VIEW - requires instance of [[ViewTable]]
  * @param FSM - requires instance of [[FSM]]
  * @param M - requires [[Monoid]] instance for your aggregate
  * @param Ex - requires function that can extract aggregate id from event
  * @tparam F - effect type
  * @tparam E - event type
  * @tparam A - aggregate type
  */
abstract class Storage[F[_]: Effect, E, A](implicit LOG: LogTable[F, E],
                                           VIEW: ViewTable[F, A],
                                           FSM: FSM[F, A, E],
                                           // its probably good idea to have Const[A,E] instead of Monoid[A]
                                           M: Monoid[A],
                                           Ex: Extractor[E]) {
  def logAndInsert(e: E) =
    fs2.Stream.eval(for {
      id <- Ex.extract(e).pure[F]
      es <- LOG.select(id).compile.toList
      st <- FSM.many(es :+ e).run(M.empty)
      _ <- LOG.insert(e)
      _ <- VIEW.insert(st._1)
    } yield st)

  def replay(id: UUID) =
    fs2.Stream.eval(for {
      es <- LOG.select(id).compile.toList
      st <- FSM.many(es).run(M.empty)
    } yield st)
}
