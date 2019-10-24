import java.util.UUID

import cats.effect.Effect
import cats.implicits._
import cats.kernel.Monoid
import state.FSM

trait Extractor[E]{
  def extract: E => UUID
}

abstract class Storage[F[_]: Effect, EVE, ENT](implicit LOG: LogTable[F,EVE],  VIEW: ViewTable[F,ENT], FSM: FSM[F,ENT,EVE], M: Monoid[ENT], extractor: Extractor[EVE]) {
  def logAndInsert(e: EVE) =
    fs2.Stream.eval(for {
      id <- extractor.extract(e).pure[F]
      es <- LOG.select(id).compile.toList
      st <- FSM.many(es :+ e).run(M.empty)
      _ <- LOG.insert(e)
      _ <- VIEW.insert(st._1)
    } yield st)
}
