package business

import java.util.UUID
import java.util.concurrent.TimeUnit

import akka.actor.{Actor, ActorRef, Props}
import akka.event.Logging
import business.dto.{Operation, Answer, UserCoin}
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
    Logger.debug("start:"+availableCandies)
    if (availableCandies == 0) noCandies() else equilibrium(availableCandies)
  }

  def equilibrium(availableCandies: Int): Actor.Receive = {
    case req@CandyMachineRequest(currentUserId, requestedOperation) =>
      Logger.debug("equilibrium:"+availableCandies+" received "+req+" from "+sender)
      requestedOperation match {
        case Operation.Coin =>
          val waitUntil: DateTime = new DateTime().plus(Conf.maxInactiveDuration)
          replace(withCoin(new UserCoin(currentUserId, waitUntil), availableCandies))
          context.system.scheduler.scheduleOnce(FiniteDuration(Conf.maxInactiveDuration.getSeconds, TimeUnit.SECONDS)) {
            self ! CandyMachineRequest(UUID.randomUUID().toString, Operation.ExpireCoin)
          }
          sender ! Answer.Success
        case Operation.Candy =>
          Logger.debug("Sending {NoCoinsInTheQueue} answer back to "+sender)
          sender ! Answer.NoCoinsInTheQueue
        case Operation.Refill =>
          replace(equilibrium(1 + availableCandies))
        case Operation.ExpireCoin =>
      }
  }

  def withCoin(locker: UserCoin, availableCandies: Int, reqQueue: Queue[(ActorRef,CandyMachineRequest)] = Queue()): Actor.Receive = {
    case req@CandyMachineRequest(currentUserId, requestedOperation) =>
      Logger.debug("withCoin:"+locker+","+availableCandies+","+reqQueue)
      if (currentUserId == locker.userId) {
        requestedOperation match {
          case Operation.Candy =>
            replace(restart(availableCandies - 1, reqQueue))
            sender ! Answer.Success
          case Operation.Coin =>
            sender ! Answer.CannotInsertMoreThanOneCoin
        }
      } else {
        val newQueue = reqQueue.enqueue((sender(),req))
        if (new DateTime().compareTo(locker.expiryTime) >= 0) {
          replace(restart(availableCandies, newQueue))
        } else {
          replace(withCoin(locker, availableCandies, newQueue))
        }
      }
  }

  def restart(newAvailableCandies: Int, reqQueue: Queue[(ActorRef,CandyMachineRequest)]): Actor.Receive = {
    Logger.debug("restart:"+newAvailableCandies+","+reqQueue)
    if (reqQueue.isEmpty) {
      start(newAvailableCandies)
    } else {
      val newActor = context.actorOf(Props(classOf[CandyMachineActor], newAvailableCandies))
      for ((enqSender,enqReq) <- reqQueue) {
        if (!Operation.ExpireCoin.eq(enqReq.operation)) {
          Logger.debug("Enq "+enqReq+" to "+newActor+" from sender="+enqSender)
          newActor tell (enqReq, enqSender)
        }
      }
      proxy(newActor)
    }
  }

  def noCandies(): Actor.Receive = {
    case CandyMachineRequest(_, Operation.Refill) =>
      replace(start(1))
    case CandyMachineRequest(_, Operation.Coin | Operation.Refill) =>
      sender ! Answer.NoMoreCandies
    case CandyMachineRequest(_, Operation.ExpireCoin) =>
  }

  //in the current solution we chain proxies whenever there are waiting reqs to ensure that they are going first and then the inbox tail
  //clearly needs rewrite, to have just one proxy step, not a proxy chain which is does not have a bound in memory or processing in the long term
  def proxy(delegate: ActorRef): Actor.Receive = {
    case req =>
      Logger.debug("proxy: fw "+req+" from sender="+sender)
      delegate forward req
  }

  def replace(newReceive: Receive): Unit = {
    context.become(newReceive, discardOld = true)
  }
  override def unhandled(message: Any): Unit = {
    Logger.warn("Could not handle "+message)
    super.unhandled(message)
  }
}