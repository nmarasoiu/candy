package business

import java.util.UUID
import java.util.concurrent.TimeUnit

import akka.actor.{Actor, ActorRef, Props}
import akka.event.Logging
import business.dto.{Answer, UserCoin}
import models.CandyMachineRequest
import org.joda.time.DateTime
import play.api.Logger

import scala.collection.immutable.Queue
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.FiniteDuration

class CandyMachineActor(initialAvailableCandies: Int) extends Actor {
  val log = Logging(context.system, this)

  def receive = start(initialAvailableCandies)

  def start(availableCandies: Int): Actor.Receive ={
    Logger.info("start:"+availableCandies)
    if (availableCandies == 0) noCandies() else equilibrium(availableCandies)
  }

  def equilibrium(availableCandies: Int): Actor.Receive = {
    case CandyMachineRequest(currentUserId, requestedOperation) =>
      Logger.info("equilibrium:"+availableCandies)
      requestedOperation match {
        case "coin" =>
          val waitUntil: DateTime = new DateTime().plus(Conf.maxInactiveDuration)
          replace(withCoin(new UserCoin(currentUserId, waitUntil), availableCandies))
          context.system.scheduler.scheduleOnce(FiniteDuration(Conf.maxInactiveDuration.getSeconds, TimeUnit.SECONDS)) {
            self ! CandyMachineRequest(UUID.randomUUID().toString, "expireCoinIfNeeded")
          }
          sender ! Answer.Success
        case "candy" =>
          sender ! Answer.NoCoinsInTheQueue
        case "refill" =>
          replace(equilibrium(1 + availableCandies))
        case "expireCoinIfNeeded" =>
      }
  }

  def withCoin(locker: UserCoin, availableCandies: Int, reqQueue: Queue[CandyMachineRequest] = Queue()): Actor.Receive = {
    case req@CandyMachineRequest(currentUserId, requestedOperation) =>
      Logger.info("withCoin:"+locker+","+availableCandies+","+reqQueue)
      if (currentUserId == locker.userId) {
        requestedOperation match {
          case "candy" =>
            replace(restart(availableCandies - 1, reqQueue))
            sender ! Answer.Success
          case "coin" =>
            sender ! Answer.CannotInsertMoreThanOneCoin
        }
      } else {
        val newQueue = reqQueue.enqueue(req)
        if (new DateTime().compareTo(locker.expiryTime) >= 0) {
          replace(restart(availableCandies, newQueue))
        } else {
          replace(withCoin(locker, availableCandies, newQueue))
        }
      }
  }

  def restart(newAvailableCandies: Int, reqQueue: Queue[CandyMachineRequest]): Actor.Receive = {
    Logger.info("restart:"+newAvailableCandies+","+reqQueue)
    if (reqQueue.isEmpty) {
      start(newAvailableCandies)
    } else {
      val newActor = context.actorOf(Props(classOf[CandyMachineActor], newAvailableCandies))
      for (enqReq <- reqQueue) {
        if (!"expireCoinIfNeeded".eq(enqReq.operation)) {
          newActor forward enqReq
        }
      }
      proxy(newActor)
    }
  }

  def noCandies(): Actor.Receive = {
    case CandyMachineRequest(_, "refill") =>
      replace(start(1))
    case CandyMachineRequest(_, "coin" | "refill") =>
      sender ! Answer.NoMoreCandies
    case CandyMachineRequest(_, "expireCoinIfNeeded") =>
  }

  //in the current solution we chain proxies whenever there are waiting reqs to ensure that they are going first and then the inbox tail
  //clearly needs rewrite, to have just one proxy step, not a proxy chain which is does not have a bound in memory or processing in the long term
  def proxy(delegate: ActorRef): Actor.Receive = {
    case req =>
      Logger.info("proxy:"+req)
      delegate forward req
  }

  def replace(newReceive: Receive): Unit = {
    context.become(newReceive, discardOld = true)
  }
}