package business

import java.util.concurrent.TimeUnit

import akka.actor.{Actor, Stash}
import business.dto.{Answer, Operation, UserLock}
import models.CandyMachineRequest
import org.joda.time.DateTime
import play.api.Logger

import scala.concurrent.duration.FiniteDuration

class CandyMachineActor(initialAvailableCandies: Int) extends Actor with Stash {
  private val log = Logger
  //Logging(context.system, this)
  private val maxInactiveDuration = Conf.maxInactiveDuration
  private val maxInactiveFiniteDuration: FiniteDuration = FiniteDuration(maxInactiveDuration.getMillis, TimeUnit.MILLISECONDS)

  override def unhandled(message: Any) {
    log.warn("Could not handle " + message)
    super.unhandled(message)
  }

  def receive = start(Math.max(0, initialAvailableCandies))

  private def start(availableCandies: Int): Actor.Receive = {
    log.debug("start:" + availableCandies)
    if (availableCandies == 0) noCandies() else equilibrium(availableCandies)
  }

  private def equilibrium(availableCandies: Int): Actor.Receive = {
    case req@CandyMachineRequest(currentUserId, requestedOperation) =>
      log.debug("equilibrium:" + availableCandies + " received " + req + " from " + sender)
      requestedOperation match {
        case Operation.Coin =>
          replace(withCoin(new UserLock(currentUserId.get, expiryTime), availableCandies))
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
      self ! CandyMachineRequest(Operation.ExpireCoin)
    }
  }

  private def withCoin(locker: UserLock, availableCandies: Int): Actor.Receive = {
    case req@CandyMachineRequest(Some(currentUserId), requestedOperation) =>
      if (currentUserId == locker.userId) {
        requestedOperation match {
          case Operation.Candy =>
            replace(restart(availableCandies - 1))
            sendOK()
          case Operation.Coin =>
            replace(withCoin(new UserLock(locker.userId, expiryTime), availableCandies))
            send(Answer.CannotInsertMoreThanOneCoin)
        }
      } else {
        requestedOperation match {
          case Operation.Candy | Operation.Coin =>
            maybeExpireCoin(locker, availableCandies)
            stash()
        }
      }
    case req@CandyMachineRequest(None, requestedOperation) =>
      requestedOperation match {
        case Operation.ExpireCoin =>
          maybeExpireCoin(locker, availableCandies)
        case Operation.Refill =>
          replace(withCoin(locker, availableCandies + 1))
          sendOK()
      }
  }

  private def maybeExpireCoin(locker: UserLock, availableCandies: Int) {
    if (new DateTime().compareTo(locker.expiryTime) >= 0) {
      replace(restart(availableCandies))
    } else {
      replace(withCoin(locker, availableCandies))
    }
  }

  private def restart(newAvailableCandies: Int): Actor.Receive = {
    log.debug("restart:" + newAvailableCandies)
    unstashAll()
    start(newAvailableCandies)
  }

  private def noCandies(): Actor.Receive = {
    case CandyMachineRequest(_, Operation.Refill) =>
      replace(start(1))
    case CandyMachineRequest(_, Operation.Coin | Operation.Candy) =>
      send(Answer.NoMoreCandies)
    case CandyMachineRequest(_, Operation.ExpireCoin) =>
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