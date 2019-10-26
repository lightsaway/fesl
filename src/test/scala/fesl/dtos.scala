package fesl

import java.util.UUID

import cats.Eval
import cats.data.{Const, StateT}
import cats.effect.IO
import cats.implicits._
import fesl.ExtractUUID.ExtractUUID

sealed trait Transaction {
  val money: Int
  val id: UUID
}

case class AccountCreated(id: UUID, money: Int) extends Transaction
case class Fill(id: UUID, money: Int) extends Transaction
case class Withdraw(id: UUID, money: Int) extends Transaction

case class Account(id: UUID, ballance: Int)

case object Account {
  implicit val idExtractor: ExtractUUID[Account] = _.id
  implicit val const =
    Const[Account, Transaction](Account(UUID.randomUUID(), 0))
}

object Transaction {

  implicit val fsm: FSM[Eval, Account, Transaction] = (e: Transaction) =>
    StateT[Eval, Account, Transaction] { account =>
      e match {
        case Fill(_, x) =>
          Eval.now(account.copy(ballance = account.ballance + x)).tupleRight(e)
        case Withdraw(_, x) =>
          Eval.now(account.copy(ballance = account.ballance - x)).tupleRight(e)
      }
  }

  implicit val fsmIO: FSM[IO, Account, Transaction] = (e: Transaction) =>
    StateT[IO, Account, Transaction] { account =>
      (account, e) match {
        case (Account.const.getConst, x: AccountCreated) =>
          Account(x.id, x.money).pure[IO].tupleRight(e)
        case (_, Fill(_, x)) =>
          IO(account.copy(ballance = account.ballance + x)).tupleRight(e)
        case (_, Withdraw(_, x)) =>
          IO(account.copy(ballance = account.ballance - x)).tupleRight(e)
      }
  }

  implicit val idExtractor: ExtractUUID[Transaction] = _.id

}
