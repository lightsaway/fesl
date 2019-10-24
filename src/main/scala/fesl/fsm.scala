package fesl

import cats.data.StateT
import cats.syntax.flatMap._
import cats.syntax.foldable._
import cats.syntax.functor._
import cats.{Foldable, Functor, Monad}

trait FSM[F[_], ENT, EVE] {
  def one(e: EVE): StateT[F, ENT, EVE]
  def many[H[_]: Foldable: Functor](es: H[EVE])(
      implicit M: Monad[F]): StateT[F, ENT, H[EVE]] = StateT[F, ENT, H[EVE]] {
    entity =>
      val result = es.foldLeft(M.pure(entity))((b, event) =>
        b.flatMap(b => one(event).run(b)).map(_._1))
      result.tupleRight(es)
  }
}
