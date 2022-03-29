package com.rockthejvm.akka.cassandra.http

import cats.data.ValidatedNel
import cats.implicits._

package object routes extends Validation

trait Validation {

  trait Required[F] extends (F => Boolean)
  trait Minimum[F]  extends ((F, Double) => Boolean) // for numerical fields

  implicit val minimumDouble: Minimum[Double] = _ >= _
  implicit val minimumInt: Minimum[Int] = _ >= _
  implicit val requiredString: Required[String] = _.nonEmpty

  def required[F: Required](field: F): Boolean = implicitly[F](field)

  def minimum[F: Minimum](field: F, limit: Int): Boolean = {
    val min: Minimum[F] = implicitly[Minimum[F]]
    min(field, limit)
  }

  type ValidationResult[A] = ValidatedNel[ValidationFailure, A]

  trait Validator[A] {
    def validate(toValidate: A): ValidationResult[A]
  }

  def validateEntity[A: Validator](entity: A): ValidationResult[A] =
    implicitly[Validator[A]].validate(entity)

  sealed trait ValidationFailure {
    def errorMessage: String
  }

  case class EmptyField(fieldName: String) extends ValidationFailure {
    override def errorMessage: String = s"$fieldName is empty"
  }

  case class NegativeValue(fieldName: String) extends ValidationFailure {
    override def errorMessage: String = s"$fieldName is negative"
  }

  case class BelowMinimumValue(fieldName: String, min: Int) extends ValidationFailure {
    override def errorMessage: String = s"$fieldName has value below the minimum requred $min"
  }

  def validateRequired[F: Required](field: F, fieldName: String): ValidationResult[F] =
    if (required(field)) field.validNel else EmptyField(fieldName).invalidNel

  def validateMinimum[F: Minimum](field: F, limit: Int, fieldName: String): ValidationResult[F] = {
    if (minimum(field, limit)) field.validNel
    else if (limit == 0) NegativeValue(fieldName).invalidNel
    else BelowMinimumValue(fieldName, limit).invalidNel
  }
}
