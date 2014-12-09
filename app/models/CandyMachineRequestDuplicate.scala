package models

import play.api.libs.json._
//until i know how to magically convert to Operation.Value
case class CandyMachineRequestDuplicate(userId: String, operation: String)

object CandyMachineRequestDuplicate {
  implicit val personFormat = Json.format[CandyMachineRequestDuplicate]
}