package com.rockthejvm.akka.cassandra

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import com.rockthejvm.akka.cassandra.PersistentBankAccount.{BankAccount, Command, CreateBankAccount, UpdateBalance}

import java.util.UUID

object Bank {

  // Responses
  final case class BankAccountCreatedResponse(id: String)
  final case class BankAccountBalanceUpdatedResponse(newBalance: Double) // TODO Maybe, we can return the whole updated object?
  final case class GetBankAccountResponse(maybeBankAccount: Option[BankAccount])

  def apply(): Behavior[Command] = registry(Map.empty)

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
        // TODO Implement other cases
      }
    }
}
