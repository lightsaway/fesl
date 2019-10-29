package fesl

import java.util.UUID

import cats.{ApplicativeError, Eval, Id, Monad, MonadError}
import cats.data.{Const, StateT}
import cats.effect.{Effect, IO}
import cats.implicits._
import fesl.ExtractUUID.ExtractUUID

sealed trait Transaction {
  val id: UUID
}

case class AccountCreated(id: UUID, money: Int) extends Transaction
case class Fill(id: UUID, money: Int)           extends Transaction
case class Withdraw(id: UUID, money: Int)       extends Transaction
case class Block(id: UUID)                      extends Transaction

case class Account(id: UUID, ballance: Int, isActive: Boolean = false, seqNr: Int = 0)

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
        case _ => Eval.later(throw new IllegalStateException(s"invalid operation"))
      }
  }
  implicit val fsmIO: FSM[IO, Account, Transaction] = (e: Transaction) =>
    StateT[IO, Account, Transaction] { account =>
      (account, e) match {
        case (Account.const.getConst, x: AccountCreated) =>
          Account(x.id, x.money, isActive = true).pure[IO].tupleRight(e)
        case (_, Fill(_, x)) if account.isActive =>
          IO(account.copy(ballance = account.ballance + x, seqNr = account.seqNr + 1)).tupleRight(e)
        case (_, Withdraw(_, x)) if account.isActive =>
          IO(account.copy(ballance = account.ballance - x, seqNr = account.seqNr + 1)).tupleRight(e)
        case (_, Block(_)) if account.isActive =>
          IO(account.copy(isActive = false, seqNr = account.seqNr + 1)).tupleRight(e)
        case _ => IO.raiseError(new IllegalStateException(s"invalid operation"))
      }
  }

  implicit val idExtractor: ExtractUUID[Transaction] = _.id

}
