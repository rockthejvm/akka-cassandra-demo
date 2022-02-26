package com.rockthejvm.akka.cassandra

object Bank {

  // Domain object
  final case class BankAccount(id: String, user: String, currency: String, balance: Double) // Don't use Double for money in production!

  // Responses
  final case class GetBankAccountResponse(maybeBankAccount: Option[BankAccount])
}
