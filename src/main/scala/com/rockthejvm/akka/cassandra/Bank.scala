package com.rockthejvm.akka.cassandra

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import com.rockthejvm.akka.cassandra.PersistentBankAccount.{BankAccount, Command, CreateBankAccount, GetBankAccount, GetBankAccountResponse, UpdateBalance}

import java.util.UUID

object Bank {

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
