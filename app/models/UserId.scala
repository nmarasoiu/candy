package models

import play.api.libs.json._

case class UserId(userId: String, operation:String)

object UserId {
  
  implicit val personFormat = Json.format[UserId]
}