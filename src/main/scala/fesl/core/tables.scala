package fesl.core

import java.util.UUID

import fs2.Stream

trait LogTable[F[_], E] {
  def insert(e: E): F[E]

  def select(id: UUID): Stream[F, E]
}

trait ViewTable[F[_], E] {
  def insert(e: E): F[E]

  def select(id: UUID): F[Option[E]]
}
