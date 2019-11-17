package fesl.skunk

import cats.effect.Bracket

object types {
  type BracketThrow[F[_]] = Bracket[F, Throwable]
}
