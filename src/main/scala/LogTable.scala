import java.util.UUID
import fs2.Stream

trait LogTable[F[_], E] {
  def insert(e: E): F[Unit]

  def select(id: UUID): Stream[F, E]

  def createTable: F[Int]
}
