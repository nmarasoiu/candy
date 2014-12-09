package models

import play.api.libs.json._

case class CandyMachineRequestDuplicate(userId: String, operation: String)

object CandyMachineRequestDuplicate {
  implicit val personFormat = Json.format[CandyMachineRequestDuplicate]
}