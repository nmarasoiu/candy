package business

import akka.actor.{Actor, ActorRef, Props}
import akka.event.Logging
import business.dto.{Answer, UserCoin}
import models.CandyMachineRequest
import org.joda.time.DateTime

import scala.collection.immutable.Queue

class CandyMachineActor(initialAvailableCandies: Int) extends Actor {
  val log = Logging(context.system, this)

  def start(candies: Int): Actor.Receive =
    if (candies == 0) noCandies() else equilibrium(candies)

  def receive = start(initialAvailableCandies)

  def equilibrium(availableCandies: Int): Actor.Receive = {
    case CandyMachineRequest(currentUserId, requestedOperation) =>
      requestedOperation match {
        case "coin" =>
          val waitUntil: DateTime = new DateTime().plus(Conf.maxInactiveDuration)
          replace(withCoin(new UserCoin(currentUserId, waitUntil), availableCandies))
          sender ! Answer.Success
        case "candy" =>
          sender ! Answer.NoCoinsInTheQueue
        case "refill" =>
          replace(equilibrium(1 + availableCandies))

      }
  }

  def restart(newAvailableCandies: Int, reqQueue: Queue[CandyMachineRequest]): Actor.Receive = {
    if (reqQueue.isEmpty) {
      start(newAvailableCandies)
    } else {
      val newActor = context.actorOf(Props(classOf[CandyMachineActor], newAvailableCandies))
      for (enqReq <- reqQueue) {
        newActor forward enqReq
      }
      proxy(newActor)
    }
  }
  def withCoin(locker: UserCoin, availableCandies: Int, reqQueue: Queue[CandyMachineRequest] = Queue()): Actor.Receive = {
    case req@CandyMachineRequest(currentUserId, requestedOperation) =>
      if (currentUserId == locker.userId) {
        requestedOperation match {
          case "candy" =>
            replace(restart(availableCandies - 1, reqQueue))
            sender ! Answer.Success
          case "coin" =>
            sender ! Answer.CannotInsertMoreThanOneCoin
          case "refill" =>
            replace(withCoin(locker, 1 + availableCandies, reqQueue))
        }
      } else {
        val newQueue: Queue[CandyMachineRequest] = reqQueue.enqueue(req)
        if (new DateTime().compareTo(locker.expiryTime) >= 0) {
          replace(restart(availableCandies, newQueue))
        } else {
          replace(withCoin(locker, availableCandies, newQueue))
        }
      }
  }

  def noCandies(): Actor.Receive = {
    case CandyMachineRequest(_, "refill") =>
      replace(start(1))
    case "coin" | "refill" =>
      sender ! Answer.NoMoreCandies
  }

  //in the current solution we chain proxies whenever there are waiting reqs to ensure that they are going first and then the inbox tail
  //clearly needs rewrite, to have just one proxy step, not a proxy chain which is does not have a bound in memory or processing in the long term
  def proxy(delegate: ActorRef): Actor.Receive = {
    case req =>
      delegate forward req
  }

  def replace(newReceive: Receive): Unit = {
    context.become(newReceive, discardOld = true)
  }
}