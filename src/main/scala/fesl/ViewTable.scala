package fesl

import java.util.UUID

trait ViewTable[F[_], E] {
  def insert(e: E): F[Unit]

  def select(id: UUID): F[E]

  def createTable: F[Int]
}
