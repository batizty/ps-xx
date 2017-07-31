package angela.core

import angela.exception.{PullMessageException, PushMessageException}
import angela.utils.Logging
import glint.models.client.BigVector
import org.apache.hadoop.conf.Configuration
import org.joda.time.DateTime

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext}
import scala.reflect.runtime.universe.TypeTag
import scala.util.{Failure, Success}

/**
  * Created by tuoyu on 04/07/2017.
  */
class GlintPSClientHandler[V](
                               vector: BigVector[V],
                               isAsync: Boolean = true
                             )(implicit val timeout: Duration, ec: ExecutionContext)
  extends PSClientHandler[V] with Logging with Serializable {

  val maxRetry = 3
  val retrySleepMillis = 1000

  def _pullAsync(keys: Array[Long])(f: (Array[V]) => Unit): Unit = {
    val _stime = DateTime.now

    def loop(retry: Int = 0): Unit = {
      if (retry >= maxRetry) {
        val err = new PullMessageException(s"Pull Failed $retry times with Errors")
        logError(s"Pull Error times $retry reach MaxRetryTimes($maxRetry)", err)
        throw err
      }

      Thread.sleep(retrySleepMillis * retry)

      vector.pull(keys) onComplete {
        case Success(w) =>
          logDebug(s" Pull Data ${keys.size} Used ${logUsedTime(_stime)} ms")
          f(w)
        case Failure(err) =>
          logError(s" $retry th Pull Failed", err)
          loop(retry + 1)
      }
    }

    loop()
  }

  def _pullSync(keys: Array[Long])(f: (Array[V]) => Unit): Unit = {
    val _stime = DateTime.now

    val ready = Await.ready[Array[V]](vector.pull(keys), timeout)
    ready onComplete {
      case Success(w) =>
        logDebug(s"Pull Data Sync ${keys.size} Used ${logUsedTime(_stime)} ms")
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
    val _stime = DateTime.now

    def loop(retry: Int = 0): Unit = {
      if (retry >= maxRetry) {
        val err = new PushMessageException(s"Push Failed $retry times with Errors")
        logError(s"Push Error times $retry reach MaxRetryTimes($maxRetry)", err)
        throw err
      }

      Thread.sleep(retrySleepMillis * retry)

      vector.push(keys, values) onComplete {
        case Success(ret: Boolean) if ret == true =>
          logDebug(s"Push Date ${keys.size} Used ${logUsedTime(_stime)} ms")
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
    val _stime = DateTime.now

    val ready = Await.ready[Boolean](vector.push(keys, values), timeout)
    ready onComplete {
      case Success(ret: Boolean) if ret == true =>
        logDebug(s"Push Date ${keys.size} Used ${logUsedTime(_stime)} ms")
        f(ret)
      case Failure(err) =>
        logError(s"Push Failed", err)
      case _ =>
        logError(s"Push Failed")
    }
  }

  override def SAVE(path: String, conf: Configuration)(f: Boolean => Unit): Unit = {
    val _stime = DateTime.now

    vector.save(path) onComplete {
      case Success(ret: Boolean) if ret == true =>
        logDebug(s" Save Date Into HDFS ${path} Used ${logUsedTime(_stime)} ms")
        f(ret)
      case Failure(err) =>
        logError(s" Save Failed Data Into HDFS ${path}", err)
      case _ =>
        logError(s" Save Failed Data Into HDFS Failed")
    }
    ()
  }


  override def DESTROY()(f: Boolean => Unit): Unit = {
    val _stime = DateTime.now

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

  private def logUsedTime(_stime: DateTime): Long = {
    DateTime.now.getMillis - _stime.getMillis
  }
}

object GlintPSClientHandler {
  implicit val timeout: Duration = 600 seconds
  implicit val ec: ExecutionContext = global

  def apply[V: TypeTag](vector: BigVector[V]): GlintPSClientHandler[V] = {
    new GlintPSClientHandler[V](vector, false)
  }
}