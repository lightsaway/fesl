package fesl

import java.util.UUID

import cats.effect.IO
import cats.implicits._
import org.scalatest.matchers.should.Matchers

class InMemoryTest extends org.scalatest.FunSuite with Matchers {
  implicit val contextShift =
    IO.contextShift(scala.concurrent.ExecutionContext.global)

  test("in memory Log test") {
    val firstSeq =
      IO(UUID.randomUUID()).map(id => id -> Seq(Fill(id, 10), Fill(id, 20)))
    val secondSeq = IO(UUID.randomUUID()).map(id =>
      id -> Seq(Fill(id, 1), Fill(id, 5), Withdraw(id, 10)))

    val test = for {
      store <- InMemoryLog.createTable[IO, Transaction]
      events1 <- firstSeq
      events2 <- secondSeq
      log = new InMemoryLog[IO, Transaction](store)
      _ <- events1._2.appendedAll(events2._2).map(log.insert).toList.sequence
      first <- log.select(events1._1).compile.toList
      second <- log.select(events2._1).compile.toList
      _ <- events1._2.map(log.insert).toList.sequence
      doubled <- log.select(events1._1).compile.toList

    } yield {
      first shouldBe events1._2.toList
      second shouldBe events2._2.toList
      doubled shouldBe events1._2.toList :++ events1._2.toList
    }

    test.unsafeRunSync()
  }

  test("in memory View test") {
    val firstSeq = IO(UUID.randomUUID()).map(id =>
      id -> Seq(Account(id, 10), Account(id, 20)))
    val secondSeq = IO(UUID.randomUUID()).map(id =>
      id -> Seq(Account(id, 1), Account(id, 5), Account(id, 10)))

    val test = for {
      store <- InMemoryView.createTable[IO, Account]
      events1 <- firstSeq
      events2 <- secondSeq
      log = new InMemoryView[IO, Account](store)
      _ <- events1._2.appendedAll(events2._2).map(log.insert).toList.sequence
      first <- log.select(events1._1)
      second <- log.select(events2._1)
    } yield {
      first shouldBe events1._2.toList.last.pure[Option]
      second shouldBe events2._2.toList.last.pure[Option]
    }
    test.unsafeRunSync()
  }

  test("in memory Storage test") {
    val id = UUID.randomUUID()
    val events = IO(id)
      .map(id => id -> Seq(AccountCreated(id, 0), Fill(id, 10), Fill(id, 20)))
      .unsafeRunSync()

    val logStore = InMemoryLog.createTable[IO, Transaction].unsafeRunSync()
    val viewStore = InMemoryView.createTable[IO, Account].unsafeRunSync()
    implicit val view = new InMemoryView[IO, Account](viewStore)
    implicit val log = new InMemoryLog[IO, Transaction](logStore)
    val storage = new Storage[IO, Transaction, Account]()

    val a = events._2.toList
      .map(storage.logAndInsert)
      .sequence
      .compile
      .toList
      .unsafeRunSync()
      .flatten
    view.select(events._1).unsafeRunSync().get shouldBe Account(events._1, 30, isActive = true, 2)
  }

}
