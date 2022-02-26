package com.rockthejvm.akka.cassandra

import akka.actor.typed.ActorSystem
import akka.util.Timeout
import com.rockthejvm.akka.cassandra.Bank.GetBankAccountResponse
import com.rockthejvm.akka.cassandra.BankAccountRoutes.{BankAccountBalanceUpdateRequest, BankAccountCreationRequest}

import scala.concurrent.Future

object BankAccountRoutes {
  final case class BankAccountCreationRequest(user: String, currency: String, balance: Double)
  final case class BankAccountBalanceUpdateRequest(currency: String, balance: Double)
}

class BankAccountRoutes(/* TODO Provide the business layer */)(implicit val system: ActorSystem[_])  {

  private implicit val timeout =
    Timeout.create(system.settings.config.getDuration("my-app.routes.ask-timeout"))

  def findBankAccount(id: String): Future[GetBankAccountResponse] = ???
  def createBankAccount(bankAccount: BankAccountCreationRequest): Future[String] = ???
  def updateBalance(id: String, request: BankAccountBalanceUpdateRequest): Future[Double] = ???

}
