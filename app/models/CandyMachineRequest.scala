package models

import play.api.libs.json._

case class CandyMachineRequest(userId: String, operation:String)

object CandyMachineRequest {
  implicit val personFormat = Json.format[CandyMachineRequest]
}