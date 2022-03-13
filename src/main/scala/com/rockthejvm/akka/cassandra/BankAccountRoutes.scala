package com.rockthejvm.akka.cassandra

import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.{ActorRef, ActorSystem}
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.headers.Location
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{Route, ValidationRejection}
import akka.util.Timeout
import cats.data.Validated.{Invalid, Valid}
import com.rockthejvm.akka.cassandra.Bank.{BankAccountBalanceUpdatedResponse, BankAccountCreatedResponse, GetBankAccountResponse}
import com.rockthejvm.akka.cassandra.BankAccountRoutes.{BankAccountBalanceUpdateRequest, BankAccountCreationRequest}
import com.rockthejvm.akka.cassandra.PersistentBankAccount.{Command, CreateBankAccount, GetBankAccount, UpdateBalance}
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
import io.circe.generic.auto._
import cats.implicits._

import scala.concurrent.{ExecutionContextExecutor, Future}
import cats.data._
import com.rockthejvm.akka.cassandra.Validation.{CurrencyIsEmpty, NegativeBalance, UserIsEmpty, ValidationResult}
object Validation {
  abstract class ValidationFailure(val message: String)

  type ValidationResult[A] = ValidatedNel[ValidationFailure, A]

  trait Validatable[A] {
    def validate: ValidationResult[A]
  }

  case object UserIsEmpty extends ValidationFailure("User is empty")
  case object CurrencyIsEmpty extends ValidationFailure("Currency is empty")
  case object NegativeBalance extends ValidationFailure("Balance must be positive")
}

object BankAccountRoutes {
  final case class BankAccountCreationRequest(
      user: String,
      currency: String,
      balance: Double
  ) extends Validation.Validatable[BankAccountCreationRequest] {

    def toCmd(replyTo: ActorRef[BankAccountCreatedResponse]): Command =
      CreateBankAccount(
        user,
        currency,
        balance,
        replyTo
      )

    override def validate: ValidationResult[BankAccountCreationRequest] = (
      validateUser,
      validateCurrency,
      validateBalance
    ).mapN(BankAccountCreationRequest)

    private def validateUser: ValidationResult[String] =
      if (user.isEmpty) UserIsEmpty.invalidNel
      else user.validNel

    private def validateCurrency: ValidationResult[String] =
      if (currency.isEmpty) CurrencyIsEmpty.invalidNel
      else currency.validNel

    private def validateBalance: ValidationResult[Double] =
      if (balance < 0) NegativeBalance.invalidNel
      else balance.validNel
  }

  final case class BankAccountBalanceUpdateRequest(currency: String, amount: Double) {
    def toCmd(id: String, replyTo: ActorRef[BankAccountBalanceUpdatedResponse]): Command =
      UpdateBalance(
        id,
        currency,
        amount,
        replyTo
      )
  }
}

class BankAccountRoutes(bank: ActorRef[Command])(implicit val system: ActorSystem[_]) {

  implicit val ec: ExecutionContextExecutor = system.executionContext

  private implicit val timeout: Timeout =
    Timeout.create(system.settings.config.getDuration("akka-cassandra-demo.routes.ask-timeout"))

  def findBankAccount(id: String): Future[GetBankAccountResponse] = {
    bank.ask(replyTo => GetBankAccount(id, replyTo))
  }

  def createBankAccount(request: BankAccountCreationRequest): Future[BankAccountCreatedResponse] =
    bank.ask(replyTo => request.toCmd(replyTo))

  def updateBalance(id: String, request: BankAccountBalanceUpdateRequest): Future[Double] =
    bank
      .ask { replyTo: ActorRef[BankAccountBalanceUpdatedResponse] =>
        request.toCmd(id, replyTo)
      }
      .map(_.newBalance)

  val bankAccountRoutes: Route =
    pathPrefix("bank-accounts") {
      concat(
        pathEnd {
          concat(post {
            entity(as[BankAccountCreationRequest]) { unvalidatedBankAccount =>
              unvalidatedBankAccount.validate match {
                case Valid(bankAccountCreationRequest) =>
                  onSuccess(createBankAccount(bankAccountCreationRequest)) { response =>
                    respondWithHeader(Location(s"/bank-accounts/${response.id}")) {
                      complete(StatusCodes.Created)
                    }
                  }
                case Invalid(errors) =>
                  reject(ValidationRejection(errors.toList.map(_.message).mkString(", ")))
              }
            }
          })
        },
        path(Segment) { id =>
          concat(
            get {
              rejectEmptyResponse {
                onSuccess(findBankAccount(id)) { response =>
                  response.maybeBankAccount match {
                    case Some(bankAccount) => complete(bankAccount)
                    case None              => complete(StatusCodes.NotFound)
                  }
                }
              }
            },
            put {
              entity(as[BankAccountBalanceUpdateRequest]) { request =>
                onSuccess(updateBalance(id, request)) { newBalance =>
                  complete((StatusCodes.OK, newBalance))
                }
              }
            }
          )
        }
      )
    }

}
