package business

import java.util.UUID
import java.util.concurrent.TimeUnit

import akka.actor.{Actor, ActorRef, Props}
import akka.event.Logging
import business.dto.{Answer, Operation, UserCoin}
import models.CandyMachineRequest
import org.joda.time.DateTime
import play.api.Logger

import scala.collection.immutable.Queue
import scala.concurrent.duration.FiniteDuration

class CandyMachineActor(initialAvailableCandies: Int) extends Actor {
  private val log = Logging(context.system, this)
  private val maxInactiveDuration = Conf.maxInactiveDuration
  private val maxInactiveFiniteDuration: FiniteDuration = FiniteDuration(maxInactiveDuration.getMillis, TimeUnit.MILLISECONDS)

  override def unhandled(message: Any) {
    Logger.warn("Could not handle " + message)
    super.unhandled(message)
  }

  def receive = start(Math.max(0, initialAvailableCandies))

  private def start(availableCandies: Int): Actor.Receive = {
    Logger.debug("start:" + availableCandies)
    if (availableCandies == 0) noCandies() else equilibrium(availableCandies)
  }

  private def equilibrium(availableCandies: Int): Actor.Receive = {
    case req@CandyMachineRequest(currentUserId, requestedOperation) =>
      Logger.debug("equilibrium:" + availableCandies + " received " + req + " from " + sender)
      requestedOperation match {
        case Operation.Coin =>
          replace(withCoin(new UserCoin(currentUserId, expiryTime), availableCandies))
          scheduleExpiryCheck()
          sendOK()
        case Operation.Refill =>
          replace(equilibrium(1 + availableCandies))
          sendOK()
        case Operation.Candy =>
          send(Answer.NoCoinsInTheQueue)
        case Operation.ExpireCoin =>
      }
  }

  private def expiryTime = new DateTime().plus(maxInactiveDuration)

  private def scheduleExpiryCheck() {
    import context.dispatcher
    context.system.scheduler.scheduleOnce(maxInactiveFiniteDuration) {
      self ! CandyMachineRequest(UUID.randomUUID().toString, Operation.ExpireCoin)
    }
  }

  private def withCoin(locker: UserCoin, availableCandies: Int, reqQueue: Queue[(ActorRef, CandyMachineRequest)] = Queue()): Actor.Receive = {
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
        requestedOperation match {
          case Operation.Candy | Operation.Coin =>
            maybeExpireCoin(locker, availableCandies, reqQueue.enqueue((sender(), req)))
          case Operation.ExpireCoin =>
            maybeExpireCoin(locker, availableCandies, reqQueue)
          case Operation.Refill =>
            replace(withCoin(locker, availableCandies + 1, reqQueue))
            sendOK()
        }
      }
  }

  private  def maybeExpireCoin(locker: UserCoin, availableCandies: Int, reqQueue: Queue[(ActorRef, CandyMachineRequest)]) {
    if (new DateTime().compareTo(locker.expiryTime) >= 0) {
      replace(restart(availableCandies, reqQueue))
    } else {
      replace(withCoin(locker, availableCandies, reqQueue))
    }
  }

  private def restart(newAvailableCandies: Int, reqQueue: Queue[(ActorRef, CandyMachineRequest)]): Actor.Receive = {
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

  private def noCandies(): Actor.Receive = {
    case CandyMachineRequest(_, Operation.Refill) =>
      replace(start(1))
    case CandyMachineRequest(_, Operation.Coin | Operation.Candy) =>
      send(Answer.NoMoreCandies)
    case CandyMachineRequest(_, Operation.ExpireCoin) =>
  }

  //in the current solution we chain proxies whenever there are waiting reqs to ensure that they are going first and then the inbox tail
  //clearly needs rewrite, to have just one proxy step, not a proxy chain which is does not have a bound in memory or processing in the long term
  private def proxy(delegate: ActorRef): Actor.Receive = {
    case req =>
      Logger.debug("proxy: fw " + req + " from sender=" + sender)
      delegate forward req
  }

  private def replace(newReceive: Receive) {
    context.become(newReceive, discardOld = true)
  }

  private def sendOK() {
    send(Answer.Success)
  }

  private def send(answer: Answer.Value) {
    sender ! answer
  }
}