package com.rockthejvm.akka.cassandra

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import com.rockthejvm.akka.cassandra.BankAccount.{BankAccount, Command, CreateBankAccount}

object Bank {

  // Responses
  final case class BankAccountCreatedResponse(id: String)
  final case class GetBankAccountResponse(maybeBankAccount: Option[BankAccount])

  def apply(): Behavior[Command] = registry(Map.empty)

  private def registry(bankAccounts: Map[String, ActorRef[_]]): Behavior[Command] =
    Behaviors.receiveMessage { case CreateBankAccount(user, currency, initialBalance, replyTo) =>
      ???
    }
}
