package business

import java.util.UUID
import java.util.concurrent.TimeUnit

import akka.actor.{Actor, ActorRef, Props}
import akka.event.Logging
import business.dto.{Answer, Operation, UserCoin}
import models.CandyMachineRequest
import org.joda.time.{DateTime, Period}
import play.api.Logger

import scala.collection.immutable.Queue
import scala.concurrent.duration.FiniteDuration

class CandyMachineActor(initialAvailableCandies: Int,
                        maxInactiveDuration: Period = Conf.maxInactiveDuration,
                        maxInactiveFiniteDuration: FiniteDuration = FiniteDuration(maxInactiveDuration.getMillis, TimeUnit.MILLISECONDS)
                         ) extends Actor {
  val log = Logging(context.system, this)

  def receive = start(Math.max(0, initialAvailableCandies))

  def start(availableCandies: Int): Actor.Receive = {
    Logger.debug("start:" + availableCandies)
    if (availableCandies == 0) noCandies() else equilibrium(availableCandies)
  }

  def equilibrium(availableCandies: Int): Actor.Receive = {
    case req@CandyMachineRequest(currentUserId, requestedOperation) =>
      Logger.debug("equilibrium:" + availableCandies + " received " + req + " from " + sender)
      requestedOperation match {
        case Operation.Coin =>
          val waitUntil: DateTime = new DateTime().plus(maxInactiveDuration)
          replace(withCoin(new UserCoin(currentUserId, waitUntil), availableCandies))
          context.system.scheduler.scheduleOnce(maxInactiveFiniteDuration) {
            self ! CandyMachineRequest(UUID.randomUUID().toString, Operation.ExpireCoin)
          }
          sendOK()
        case Operation.Candy =>
          send(Answer.NoCoinsInTheQueue)
        case Operation.Refill =>
          replace(equilibrium(1 + availableCandies))
          sendOK()
        case Operation.ExpireCoin =>
      }
  }

  def withCoin(locker: UserCoin, availableCandies: Int, reqQueue: Queue[(ActorRef, CandyMachineRequest)] = Queue()): Actor.Receive = {
    case req@CandyMachineRequest(currentUserId, requestedOperation) =>
      Logger.debug("withCoin:" + locker + "," + availableCandies + "," + reqQueue)
      if (currentUserId == locker.userId) {
        requestedOperation match {
          case Operation.Candy =>
            replace(restart(availableCandies - 1, reqQueue))
            sendOK()
          case Operation.Coin =>
            send(Answer.CannotInsertMoreThanOneCoin)
        }
      } else {
        def maybeExpireCoin(newQueue: Queue[(ActorRef, CandyMachineRequest)]) {
          if (new DateTime().compareTo(locker.expiryTime) >= 0) {
            replace(restart(availableCandies, newQueue))
          } else {
            replace(withCoin(locker, availableCandies, newQueue))
          }
        }
        requestedOperation match {
          case Operation.Candy | Operation.Coin =>
            maybeExpireCoin(reqQueue.enqueue((sender(), req)))
          case Operation.ExpireCoin =>
            maybeExpireCoin(reqQueue)
          case Operation.Refill =>
            replace(withCoin(locker, availableCandies + 1, reqQueue))
            sendOK()
        }
      }
  }

  def restart(newAvailableCandies: Int, reqQueue: Queue[(ActorRef, CandyMachineRequest)]): Actor.Receive = {
    Logger.debug("restart:" + newAvailableCandies + "," + reqQueue)
    if (reqQueue.isEmpty) {
      start(newAvailableCandies)
    } else {
      val newActor = context.actorOf(Props(classOf[CandyMachineActor], newAvailableCandies))
      for ((enqSender, enqReq) <- reqQueue) {
        Logger.debug("Enq " + enqReq + " to " + newActor + " from sender=" + enqSender)
        newActor tell(enqReq, enqSender)
      }
      proxy(newActor)
    }
  }

  def noCandies(): Actor.Receive = {
    case CandyMachineRequest(_, Operation.Refill) =>
      replace(start(1))
    case CandyMachineRequest(_, Operation.Coin | Operation.Candy) =>
      send(Answer.NoMoreCandies)
    case CandyMachineRequest(_, Operation.ExpireCoin) =>
  }

  //in the current solution we chain proxies whenever there are waiting reqs to ensure that they are going first and then the inbox tail
  //clearly needs rewrite, to have just one proxy step, not a proxy chain which is does not have a bound in memory or processing in the long term
  def proxy(delegate: ActorRef): Actor.Receive = {
    case req =>
      Logger.debug("proxy: fw " + req + " from sender=" + sender)
      delegate forward req
  }

  def replace(newReceive: Receive) {
    context.become(newReceive, discardOld = true)
  }

  override def unhandled(message: Any) {
    Logger.warn("Could not handle " + message)
    super.unhandled(message)
  }

  def send(answer: Answer.Value) {
    sender ! answer
  }

  def sendOK() {
    send(Answer.Success)
  }
}