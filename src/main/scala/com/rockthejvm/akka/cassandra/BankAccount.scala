package com.rockthejvm.akka.cassandra

import akka.actor.typed.ActorRef
import akka.persistence.typed.scaladsl.Effect
import com.rockthejvm.akka.cassandra.Bank.BankAccountCreatedResponse

object BankAccount {

  // Commands
  sealed trait Command
  final case class CreateBankAccount(
      user: String,
      currency: String,
      initialBalance: Double,
      replyTo: ActorRef[BankAccountCreatedResponse]  // TODO Change this type
  ) extends Command

  // Events
  sealed trait Event
  final case class BankAccountCreated(id: String) extends Event

  // Domain object
  final case class BankAccount(
      id: String,
      user: String,
      currency: String,
      balance: Double
  ) // Don't use Double for money in production!

  private def handleCommand(command: Command): Unit = {
    command match {
      case CreateBankAccount(user, currency, initialBalance, replyTo) =>
      // TODO Add some validation logic
      // TODO Implement the effect
    }
  }
}
