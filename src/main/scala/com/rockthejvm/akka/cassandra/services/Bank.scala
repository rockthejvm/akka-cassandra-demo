package com.rockthejvm.akka.cassandra.services

import akka.actor.typed.receptionist.{Receptionist, ServiceKey}
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import akka.util.Timeout
import com.rockthejvm.akka.cassandra.services.PersistentBankAccount._

import java.util.UUID
import java.util.concurrent.TimeUnit
import scala.util.Success

object Bank {

  sealed trait Request
  final case class CreateBankAccountRequest(
      user: String,
      currency: String,
      initialBalance: Double,
      replyTo: ActorRef[BankAccountCreatedResponse]
  ) extends Request
  final case class UpdateBalanceRequest(
      id: String,
      currency: String,
      amount: Double,
      replyTo: ActorRef[BankAccountBalanceUpdatedResponse]
  ) extends Request
  private final case class ForwardToBankAccount(
      to: ActorRef[Command],
      cmd: Command
  ) extends Request
  private final case class BankAccountNotFound(replyTo: ActorRef[BankAccountBalanceUpdatedResponse])
      extends Request

  // FIXME Change this ASAP
  implicit val timeout: Timeout = Timeout.apply(100, TimeUnit.MILLISECONDS)

  def apply(): Behavior[Request] = {
    Behaviors.setup[Request] { ctx =>
      Behaviors.receiveMessagePartial {
        case CreateBankAccountRequest(user, currency, initialBalance, replyTo) =>
          val id             = UUID.randomUUID().toString
          val newBankAccount = ctx.spawn(PersistentBankAccount(id), id)
          newBankAccount ! CreateBankAccount(user, currency, initialBalance, replyTo)
          Behaviors.same
        case UpdateBalanceRequest(id, currency, amount, replyTo) =>
          val key = ServiceKey[Command](id)
          ctx.ask(
            ctx.system.receptionist,
            Receptionist.Find(key)
          ) {
            case Success(listing) =>
              listing.serviceInstances(key).headOption match {
                case Some(bankAccount) =>
                  ForwardToBankAccount(bankAccount, UpdateBalance(id, currency, amount, replyTo))
                case None =>
                  BankAccountNotFound(replyTo)
              }
          }
          Behaviors.same
        case ForwardToBankAccount(bankAccount, cmd) =>
          bankAccount ! cmd
          Behaviors.same
        case BankAccountNotFound(replyTo) =>
          // TODO: Send error message
          Behaviors.same
//        case getCmd @ GetBankAccount(id, replyTo) =>
//          bankAccounts.get(id) match {
//            case Some(bankAccount) =>
//              bankAccount ! getCmd
//            case None =>
//              replyTo ! GetBankAccountResponse(None)
//          }
//          Behaviors.same
      }
    }
  }

  private def registry(bankAccounts: Map[String, ActorRef[Command]]): Behavior[Command] =
    Behaviors.receive { (ctx, message) =>
      message match {
        case createCmd @ CreateBankAccount(_, _, _, _) =>
          val id             = UUID.randomUUID().toString
          val newBankAccount = ctx.spawn(PersistentBankAccount(id), id)
          newBankAccount ! createCmd
          registry(bankAccounts + (id -> newBankAccount))
        case updateCmd @ UpdateBalance(id, _, _, _) =>
          bankAccounts.get(id) match {
            case Some(bankAccount) =>
              bankAccount ! updateCmd
            case None =>
            // TODO: Reply with some error
          }
          Behaviors.same
        case getCmd @ GetBankAccount(id, replyTo) =>
          bankAccounts.get(id) match {
            case Some(bankAccount) =>
              bankAccount ! getCmd
            case None =>
              replyTo ! GetBankAccountResponse(None)
          }
          Behaviors.same
      }
    }
}
