package com.rockthejvm.akka.cassandra

import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.{ActorRef, ActorSystem}
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.headers.Location
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.unmarshalling.FromRequestUnmarshaller
import akka.util.Timeout
import cats.data.Validated.{Invalid, Valid}
import cats.data._
import cats.implicits._
import com.rockthejvm.akka.cassandra.Bank.{
  BankAccountBalanceUpdatedResponse,
  BankAccountCreatedResponse,
  GetBankAccountResponse
}
import com.rockthejvm.akka.cassandra.BankAccountRoutes.{
  BankAccountBalanceUpdateRequest,
  BankAccountCreationRequest
}
import com.rockthejvm.akka.cassandra.PersistentBankAccount.{
  Command,
  CreateBankAccount,
  GetBankAccount,
  UpdateBalance
}
import com.rockthejvm.akka.cassandra.Validation._
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
import io.circe.generic.auto._

import scala.concurrent.{ExecutionContextExecutor, Future}
object Validation {

  trait Required[F] extends (F => Boolean)
  trait Minimum[F]  extends ((F, Int) => Boolean)

  implicit val minimumDouble: Minimum[Double] = _ >= _

  implicit val requiredString: Required[String] = _.nonEmpty

  def required[F: Required](field: F): Boolean = implicitly[F](field)

  def minimum[F: Minimum](field: F, limit: Int): Boolean = {
    val min: Minimum[F] = implicitly[Minimum[F]]
    min(field, limit)
  }

  type ValidationResult[A] = ValidatedNel[ValidationFailure, A]

  trait Validatable[A] {
    def validate(toValidate: A): ValidationResult[A]
  }

  sealed trait ValidationFailure {
    def errorMessage: String
  }
  case class EmptyField(fieldName: String) extends ValidationFailure {
    override def errorMessage: String = s"$fieldName is empty"
  }
  case class NegativeValue(fieldName: String) extends ValidationFailure {
    override def errorMessage: String = s"$fieldName is negative"
  }

  def validateRequired[F: Required](field: F, fieldName: String): ValidationResult[F] =
    if (required(field)) field.validNel else EmptyField(fieldName).invalidNel
  def validateMinimum[F: Minimum](field: F, limit: Int, fieldName: String): ValidationResult[F] =
    if (minimum(field, limit)) field.validNel else NegativeValue(fieldName).invalidNel

}

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

  implicit val BankAccountCreationRequestValidatable: Validatable[BankAccountCreationRequest] =
    (toVal: BankAccountCreationRequest) =>
      (
        validateRequired(toVal.user, "user"),
        validateRequired(toVal.currency, "currency"),
        validateMinimum(toVal.balance, 0, "balance")
      ).mapN(BankAccountCreationRequest.apply)

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

  implicit def validatedEntityUnmarshaller[A <: Validatable[A]](implicit
      um: FromRequestUnmarshaller[A]
  ): FromRequestUnmarshaller[Valid[A]] =
    um.flatMap { _ => _ => entity =>
      entity.validate match {
        case v @ Valid(_) =>
          Future.successful(v)
        case Invalid(failures) =>
          val message = failures.toList.map(_.message).mkString(", ")
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
