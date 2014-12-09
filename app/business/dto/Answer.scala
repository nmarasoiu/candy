package business.dto

/**
 * Created by dnmaras on 12/4/14.
 */
object Answer extends Enumeration {

  val Timeout, Success, OtherUserIsUsingTheMachine, CannotInsertMoreThanOneCoin, NoMoreCandies, NoCoinsInTheQueue = Value

}
