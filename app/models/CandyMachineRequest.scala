package models

import business.dto.Operation

case class CandyMachineRequest(userId: String, operation: Operation.Value)
