package models

import business.dto.Operation
import org.joda.time.DateTime

case class CandyMachineRequest(userId: Option[String], operation: Operation.Value)

object CandyMachineRequest {
  def apply(userId: String, operation: Operation.Value): CandyMachineRequest = {
    new CandyMachineRequest(Some(userId), operation)
  }

  def apply(operation: Operation.Value): CandyMachineRequest = {
    new CandyMachineRequest(None, operation)
  }
}