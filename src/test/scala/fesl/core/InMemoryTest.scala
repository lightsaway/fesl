package fesl.core

import java.time.LocalDateTime
import java.util.UUID

import cats.effect.IO
import cats.implicits._
import fesl.core.dtos.{Account, CreateAccount, Fill, Transaction, Withdraw}
import fesl.skunk._
import org.scalatest.matchers.should.Matchers

class InMemoryTest extends org.scalatest.FunSuite with Matchers {
  implicit val contextShift =
    IO.contextShift(scala.concurrent.ExecutionContext.global)
  val date = LocalDateTime.now()
  test("in memory Log test") {

    val firstSeq =
      IO(UUID.randomUUID()).map(id => id -> Seq(Fill(id, id, 10, date), Fill(id, id, 20, date)))
    val secondSeq =
      IO(UUID.randomUUID()).map(id =>
        id -> Seq(Fill(id, id, 1, date), Fill(id, id, 5, date), Withdraw(id, id, 10, date)))

    val test = for {
      store   <- InMemoryLog.createTable[IO, Transaction]
      events1 <- firstSeq
      events2 <- secondSeq
      log = new InMemoryLog[IO, Transaction](store)
      _       <- events1._2.appendedAll(events2._2).map(log.insert).toList.sequence
      first   <- log.select(events1._1).compile.toList
      second  <- log.select(events2._1).compile.toList
      _       <- events1._2.map(log.insert).toList.sequence
      doubled <- log.select(events1._1).compile.toList

    } yield {
      first shouldBe events1._2.toList
      second shouldBe events2._2.toList
      doubled shouldBe events1._2.toList :++ events1._2.toList
    }

    test.unsafeRunSync()
  }

  test("in memory View test") {
    val firstSeq = IO(UUID.randomUUID()).map(id => id -> Seq(Account(id, 10), Account(id, 20)))
    val secondSeq =
      IO(UUID.randomUUID()).map(id => id -> Seq(Account(id, 1), Account(id, 5), Account(id, 10)))

    val test = for {
      store   <- InMemoryView.createTable[IO, Account]
      events1 <- firstSeq
      events2 <- secondSeq
      log = new InMemoryView[IO, Account](store)
      _      <- events1._2.appendedAll(events2._2).map(log.insert).toList.sequence
      first  <- log.select(events1._1)
      second <- log.select(events2._1)
    } yield {
      first shouldBe events1._2.toList.last.pure[Option]
      second shouldBe events2._2.toList.last.pure[Option]
    }
    test.unsafeRunSync()
  }

  test("in memory Storage test") {
    val id = UUID.randomUUID()

    val test = for {
      events <- IO(id).map(id =>
        id -> Seq(CreateAccount(id, id, 0, date), Fill(id, id, 10, date), Fill(id, id, 20, date)))
      log  <- InMemoryLog[IO, Transaction]()
      view <- InMemoryView[IO, Account]()
    } yield {
      implicit val l: LogTable[IO, Transaction] = log
      implicit val v: ViewTable[IO, Account]    = view
      val storage                               = new Storage[IO, Transaction, Account]()
      for {
        _   <- events._2.toList.traverse(storage.logAndInsert).compile.drain
        acc <- view.select(events._1)
        _ = acc.get shouldBe Account(events._1, 30, isActive = true, 2)
      } yield ()
    }
    test.flatten.unsafeRunSync()
  }
}
