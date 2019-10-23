import cats.{Applicative, Foldable, Functor, Id, Monad, Monoid}
import cats.data.StateT
import cats.syntax.functor._
import cats.syntax.foldable._
import cats.syntax.flatMap._

object monoid {
  trait FSM[A]{
    def one(e : A)(implicit m: Monoid[A]) = many[Id](e) // Seems to be weird use case
    def many[H[_]: Foldable](es : H[A])(implicit m: Monoid[A]) = es.foldLeft(m.empty)((b,a) => m.combine(b,a))
  }

}

object state {
  trait FSM[F[_], E, V] {
    def one(e: V): StateT[F, E, V]
    def many[H[_] : Foldable : Functor](es: H[V])(implicit M: Monad[F]): StateT[F, E, H[V]] = StateT[F, E, H[V]] { entity =>
      val result = es.foldLeft(M.pure(entity))((b, event) => b.flatMap(b => one(event).run(b)).map(_._1))
      result.tupleRight(es)
    }
  }
}
