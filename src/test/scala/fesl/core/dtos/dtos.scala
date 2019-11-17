package fesl.core.dtos

import java.time.LocalDateTime
import java.util.UUID

import cats.{Applicative, Functor, Id}
import cats.data.{Const, StateT}
import cats.effect.{Effect, IO}
import cats.implicits._
import fesl.core.ExtractUUID.ExtractUUID
import fesl.core.FSM
import fesl.skunk.codecs.{json, uuid}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import skunk.Codec
import skunk.codec.all._

sealed trait Transaction extends Product {
  val id: UUID
  val agg_id: UUID
  val appeared_on: LocalDateTime
}

case class CreateAccount(id: UUID, agg_id: UUID, money: Int, appeared_on: LocalDateTime)
    extends Transaction
case class Fill(id: UUID, agg_id: UUID, money: Int, appeared_on: LocalDateTime) extends Transaction
case class Withdraw(id: UUID, agg_id: UUID, money: Int, appeared_on: LocalDateTime)
    extends Transaction
case class Block(id: UUID, agg_id: UUID, appeared_on: LocalDateTime) extends Transaction

case class Account(id: UUID, ballance: Int, isActive: Boolean = false, seqNr: Int = 0)
case class AccountAggregate(id: UUID, state: Account, updated_on: LocalDateTime)

object AccountAggregate {
  import Account._
  implicit val idExtractor: ExtractUUID[AccountAggregate] = _.id
  implicit val codec: Codec[AccountAggregate] =
    (uuid, json[Account], timestamp(6))
      .imapN(AccountAggregate.apply)(AccountAggregate.unapply(_).get)
}

object Account {
  implicit val idExtractor: ExtractUUID[Account] = _.id
  implicit val const =
    Const[Account, Transaction](Account(UUID.randomUUID(), 0))
  import io.circe.generic.semiauto._
  implicit val encoder: io.circe.Encoder[Account] = deriveEncoder[Account]
  implicit val decoder: io.circe.Decoder[Account] = deriveDecoder[Account]
}

object Transaction {
  import cats.implicits._

  implicit val fsm: FSM[Id, Account, Transaction] = (e: Transaction) =>
    StateT[Id, Account, Transaction] { account =>
      e match {
        case x: Fill =>
          (account.copy(ballance = account.ballance + x.money), e)
        case x: Withdraw =>
          (account.copy(ballance = account.ballance - x.money), e)
        case _ => throw new IllegalStateException(s"invalid operation")
      }
  }

  def derriveFsm[F[_]](implicit F: Effect[F]): FSM[F, Account, Transaction] =
    e =>
      StateT[F, Account, Transaction] { account =>
        (account, e) match {
          case (Account.const.getConst, x: CreateAccount) =>
            Account(x.id, x.money, isActive = true)
              .pure[F]
              .tupleRight(e)
          case (_, x: Fill) if account.isActive =>
            account
              .copy(ballance = account.ballance + x.money, seqNr = account.seqNr + 1)
              .pure[F]
              .tupleRight(e)
          case (_, x: Withdraw) if account.isActive =>
            account
              .copy(ballance = account.ballance - x.money, seqNr = account.seqNr + 1)
              .pure[F]
              .tupleRight(e)
          case (_, _: Block) if account.isActive =>
            account.copy(isActive = false, seqNr = account.seqNr + 1).pure[F].tupleRight(e)
          case _ => Effect[F].raiseError(new IllegalStateException(s"invalid operation"))
        }
    }

  implicit val fsmIO: FSM[IO, Account, Transaction] = derriveFsm[IO]

  implicit val idExtractor: ExtractUUID[Transaction] = _.agg_id

  implicit val encoder: io.circe.Encoder[Transaction] = deriveEncoder[Transaction]
  implicit val decoder: io.circe.Decoder[Transaction] = deriveDecoder[Transaction]

  implicit val codec: Codec[Transaction] =
    (uuid, uuid, varchar(50), json[Transaction], timestamp(6))
      .imapN((_, _, _, t, _) => t)(x =>
        (x.id, x.agg_id, x.getClass.getSimpleName.toLowerCase, x, x.appeared_on))

  def apply() = ???
}
