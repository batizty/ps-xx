package angela.core

import akka.actor.ActorRef
import angela.exception.{NoParameterServerException, NotEnoughParameterServerException, PullMessageException, PushMessageException}
import angela.utils.Logging
import com.typesafe.config.ConfigFactory
import glint.Client
import glint.models.client.BigVector
import org.apache.hadoop.conf.Configuration
import org.joda.time.DateTime

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext}
import scala.reflect.runtime.universe.TypeTag
import scala.util.{Failure, Success}
import scala.language.postfixOps

/**
  * Created by tuoyu on 04/07/2017.
  */
class GlintPSClientHandler[V: breeze.math.Semiring : TypeTag](
                               master: String,
                               val psServerCount: Int,
                               val features: Long,
                               isAsync: Boolean = true
                             )(implicit val timeout: Duration, val ec: ExecutionContext)
  extends PSClientHandler[V] with Logging with Serializable {

  import GlintPSClientHandler._

  val maxRetry = 3
  val retrySleepMillis = 1000

  val default = ConfigFactory
    .parseResourcesAnySyntax("glint")

  val config = ConfigFactory
    .parseString(s"glint.master.host=" + master)
    .withFallback(default)
    .resolve()

  val client = Client(config, Some(psServerCount))

  val vector: BigVector[V] = validation()

  def validation(): BigVector[V] = {
    val _vector = client
      .vectorWithCyclicPartitioner[V](features)

    val servers = Await
      .result[Array[ActorRef]](client.serverList(), timeout)

    if (servers.isEmpty) {
      vector.destroy()
      throw NoParameterServerException("Server List is Empty")
    } else if (psServerCount * MinimumPsServerRatio > servers.length) {
      vector.destroy()
      throw NotEnoughParameterServerException(s"Training Needs Parameter Server Count " +
        s": ${psServerCount * MinimumPsServerRatio}, " +
        s"but now available parameter server count : ${servers.length}")
    } else if (psServerCount * AcceptablePsServerRatio > servers.length) {
      logError("Training Needs Minimum Parameter Server Count : " +
        (psServerCount * AcceptablePsServerRatio) +
        s" , now available parameter server count : ${servers.length} just fit the needs")
    }
    _vector
  }

  def _pullAsync(keys: Array[Long])(f: (Array[V]) => Unit): Unit = {
    val _stime = DateTime.now

    def loop(retry: Int = 0): Unit = {
      if (retry >= maxRetry) {
        val err = PullMessageException(s"Pull Failed $retry times with Errors")
        logError(s"Pull Error times $retry reach MaxRetryTimes($maxRetry)", err)
        throw err
      }

      Thread.sleep(retrySleepMillis * retry)

      vector.pull(keys) onComplete {
        case Success(w) =>
          logDebug(s" Pull Data ${keys.length} Used ${logUsedTime(_stime)} ms")
          f(w)
        case Failure(err) =>
          logError(s" $retry th Pull Failed", err)
          loop(retry + 1)
      }
    }

    loop()
  }

  def _pullSync(keys: Array[Long])(f: (Array[V]) => Unit): Unit = {
    val s_time = DateTime.now

    val ready = Await.ready[Array[V]](vector.pull(keys), timeout)
    ready onComplete {
      case Success(w) =>
        logDebug(s"Pull Data Sync ${keys.length} Used ${logUsedTime(s_time)} ms")
        f(w)
      case Failure(err) =>
        logError(s"Pull Failed")
        throw err
    }
  }

  override def PULL(keys: Array[Long])(f: (Array[V]) => Unit): Unit = {
    if (isAsync)
      _pullAsync(keys)(f)
    else
      _pullSync(keys)(f)
  }

  def _pushAsync(keys: Array[Long], values: Array[V])(f: Boolean => Unit): Unit = {
    val s_time = DateTime.now

    def loop(retry: Int = 0): Unit = {
      if (retry >= maxRetry) {
        val err = PushMessageException(s"Push Failed $retry times with Errors")
        logError(s"Push Error times $retry reach MaxRetryTimes($maxRetry)", err)
        throw err
      }

      Thread.sleep(retrySleepMillis * retry)

      vector.push(keys, values) onComplete {
        case Success(ret: Boolean) if ret =>
          logDebug(s"Push Date ${keys.length} Used ${logUsedTime(s_time)} ms")
          f(ret)
        case Failure(err) =>
          logError(s"Push $retry th Push Failed", err)
          loop(retry + 1)
        case _ =>
          logError(s"Push $retry th Push Failed")
          loop(retry + 1)
      }
    }

    loop()
  }


  override def PUSH(keys: Array[Long], values: Array[V])(f: Boolean => Unit): Unit = {
    val s_time = DateTime.now

    val ready = Await.ready[Boolean](vector.push(keys, values), timeout)
    ready onComplete {
      case Success(ret: Boolean) if ret =>
        logDebug(s"Push Date ${keys.length} Used ${logUsedTime(s_time)} ms")
        f(ret)
      case Failure(err) =>
        logError(s"Push Failed", err)
      case _ =>
        logError(s"Push Failed")
    }
  }

  override def SAVE(path: String, conf: Configuration)(f: Boolean => Unit): Unit = {
    val s_time = DateTime.now

    vector.save(path, Some(conf)) onComplete {
      case Success(ret: Boolean) if ret =>
        logDebug(s" Save Date Into HDFS $path Used ${logUsedTime(s_time)} ms")
        f(ret)
      case Failure(err) =>
        logError(s" Save Failed Data Into HDFS $path", err)
      case _ =>
        logError(s" Save Failed Data Into HDFS Failed")
    }
    ()
  }

  override def DESTROY()(f: Boolean => Unit): Unit = {
    vector.destroy() onComplete {
      case Success(ret: Boolean) =>
        f(ret)
      case Failure(err) =>
        logError(s" Destroy PS Handler Failure", err)
      case _ =>
        logError(s" Destroy PS Handler Failed")
    }
    ()
  }

  private def logUsedTime(sTime: DateTime): Long = {
    DateTime.now.getMillis - sTime.getMillis
  }
}

object GlintPSClientHandler {
  implicit val timeout: Duration = 600 seconds
  implicit val ec: ExecutionContext = global

  val MinimumPsServerRatio: Double = 0.5f
  val AcceptablePsServerRatio: Double = 0.8f

  def apply[V: breeze.math.Semiring : TypeTag](master: String, psServerCount: Int, features: Long): GlintPSClientHandler[V] = {
    new GlintPSClientHandler[V](master, psServerCount, features)
  }
}