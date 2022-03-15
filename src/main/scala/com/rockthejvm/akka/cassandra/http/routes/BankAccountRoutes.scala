package com.rockthejvm.akka.cassandra.http.routes

import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.{ActorRef, ActorSystem}
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.headers.Location
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.unmarshalling.FromRequestUnmarshaller
import akka.util.Timeout
import cats.data.Validated.{Invalid, Valid}
import cats.implicits._
import com.rockthejvm.akka.cassandra.Bank.{
  BankAccountBalanceUpdatedResponse,
  BankAccountCreatedResponse,
  GetBankAccountResponse
}
import com.rockthejvm.akka.cassandra.PersistentBankAccount.{
  Command,
  CreateBankAccount,
  GetBankAccount,
  UpdateBalance
}
import com.rockthejvm.akka.cassandra.http.routes.BankAccountRoutes.{
  BankAccountBalanceUpdateRequest,
  BankAccountCreationRequest
}
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
import io.circe.generic.auto._

import scala.concurrent.{ExecutionContextExecutor, Future}

object BankAccountRoutes {
  final case class BankAccountCreationRequest(
      user: String,
      currency: String,
      balance: Double
  ) {
    def toCmd(replyTo: ActorRef[BankAccountCreatedResponse]): Command =
      CreateBankAccount(
        user,
        currency,
        balance,
        replyTo
      )
  }
  object BankAccountCreationRequest {
    implicit val BankAccountCreationRequestValidable: Validable[BankAccountCreationRequest] =
      (toVal: BankAccountCreationRequest) =>
        (
          validateRequired(toVal.user, "user"),
          validateRequired(toVal.currency, "currency"),
          validateMinimum(toVal.balance, 0, "balance")
        ).mapN(BankAccountCreationRequest.apply)
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

  object BankAccountBalanceUpdateRequest {
    implicit val BankAccountBalanceUpdateRequestValidable: Validable[BankAccountBalanceUpdateRequest] =
      (toVal: BankAccountBalanceUpdateRequest) =>
        (
          validateRequired(toVal.currency, "currency"),
          toVal.amount.validNel
        ).mapN(BankAccountBalanceUpdateRequest.apply)
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

  implicit def validatedEntityUnmarshaller[A: Validable](implicit
                                                         um: FromRequestUnmarshaller[A]
  ): FromRequestUnmarshaller[Valid[A]] =
    um.flatMap { _ => _ => entity =>
      validateEntity(entity) match {
        case v @ Valid(_) =>
          Future.successful(v)
        case Invalid(failures) =>
          val message = failures.toList.map(_.errorMessage).mkString(", ")
          Future.failed(new IllegalArgumentException(message))
      }
    }

  val bankAccountRoutes: Route =
    pathPrefix("bank-accounts") {
      concat(
        pathEnd {
          concat(post {
            entity(as[Valid[BankAccountCreationRequest]]) { bankAccountCreationRequest =>
              onSuccess(createBankAccount(bankAccountCreationRequest.a)) { response =>
                respondWithHeader(Location(s"/bank-accounts/${response.id}")) {
                  complete(StatusCodes.Created)
                }
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
