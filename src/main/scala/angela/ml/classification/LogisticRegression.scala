package angela.ml.classification

import akka.actor.ActorRef
import akka.util.Timeout
import angela.core.PSClientHandler
import angela.core.GlintPSClientHandler
import org.apache.hadoop.conf.Configuration
import org.apache.spark.ml.param.ParamMap
import org.apache.spark.ml.util.Identifiable
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.Dataset
import com.typesafe.config.ConfigFactory
import glint.Client

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext}
import scala.collection.mutable
import angela.utils.Logging
import angela.exception._

/**
  * Logistic regression. Supports multinomial logistic (softmax) regression and binomial logistic
  * regression.
  */
private[classification] trait LogisticRegressionParams
  extends HasRegParam
    with HasMaxIter
    with HasFitIntercept
    with HasTol
    with HasElasticNetParam
    with ParameterServerCount
    with ParameterServerMaster
    with HasThreshold
    with NumOfFeatures
    with IsAsynchronousAlgorithm
    with BatchSize
    with TrainDataSetSplitRatio {

  def setThreshold(value: Double): this.type = {
    assert(value < 1.0f && value > 0.0, "Threshold should be in (0.0, 1.0)")
    if (isSet(threshold)) clear(threshold)
    set(threshold, value)
  }
}

class LogisticRegression(val uid: String)
  extends LogisticRegressionParams with Logging {
  val MinimumPsServerRatio: Double = 0.5f
  val AcceptablePsServerRatio: Double = 0.8f
  val LearningRateDecay: Double = 0.5

  implicit def Long2Int(x: Long): Int = {
    try {
      x toInt
    } catch {
      case err: Throwable =>
        logError(s"convert Long2Int Failed $x", err)
        throw err
    }
  }

  def this() = this(Identifiable.randomUID("logreg-withps-asgd"))

  /**
    * Set up Regular Params
    * alpha * (lamda * ||w||) + (1 - alpha) * ((lamda / 2) * (Wt * W)),
    * alpha belong to [0, 1]
    * RegParam is the this alpha
    * Default is 0.0.
    *
    * @group setParam
    */
  def setRegParam(value: Double): this.type = set(regParam, value)

  setDefault(regParam -> 0.0)


  /**
    * Set the ElasticNet mixing parameter.
    * For alpha = 0, the penalty is an L2 penalty. For alpha = 1, it is an L1 penalty.
    * For 0 < alpha < 1, the penalty is a combination of L1 and L2.
    * Default is 0.0 which is an L2 penalty.
    *
    * @group setParam
    */
  def setElasticNetParam(value: Double): this.type = set(elasticNetParam, value)

  setDefault(elasticNetParam -> 0.0)

  /**
    * Set Max Iteration Times.
    * Default is 20.
    */
  def setMaxIter(value: Int): this.type = set(maxIter, value)

  setDefault(maxIter -> 20)

  /**
    * Set the convergence tolerance of iterations.
    * Smaller value will lead to higher accuracy with cost of more iterations.
    * Default is 1E-6
    *
    * @group setParam
    */
  def setTol(value: Double): this.type = set(tol, value)

  setDefault(tol -> 1E-6)

  /**
    * Whether to fit an intercept term
    * Default is true
    *
    * @group setParam
    */
  def setFitIntercept(value: Boolean): this.type = set(fitIntercept, value)

  setDefault(fitIntercept, true)

  override def setThreshold(value: Double): this.type = super.setThreshold(value)

  override def getThreshold: Double = super.getThreshold

  override def copy(extra: ParamMap): LogisticRegression = defaultCopy(extra)

  // implicit values
  implicit val timeout: Timeout = 120 seconds
  implicit val ec: ExecutionContext = ExecutionContext.Implicits.global


  def train(dataset: Dataset[_], handlePersistence: Boolean): LogisticRegression = {
    // TODO support Spark Standard interface
    ???

  }

  private val intercept: Double = 0.0

  implicit def Map2MutableMap[V, T](m: Map[V, T]): mutable.Map[V, T] = mutable.Map[V, T](m.toSeq: _*)

  private def sigmod(weight: mutable.Map[Long, Double], x: mutable.Map[Long, Double]): Double = {
    val margin: Double = x.filterNot(_._2 == 0.0f)
      .map { case (index, value) =>
        weight.getOrElse(index, 0.0) * value
      }.sum + intercept
    1.0 / (1.0 + math.exp(-margin))
  }

  /**
    * Init Parameter Server Connection and setting up model parameters which are related with
    * model, it is preparation for later training
    * TODO parameter should could init Parameter Server Client Handler
    */
  def connect(): PSClientHandler[Double] = {
    /**
      * Later Add Detail Configurations
      */
    val config = ConfigFactory
      .parseString(s"glint.master.host=${getMaster}")

    val client = Client(config)
    val vector = client
      .vectorWithCyclicPartitioner[Double](getNumOfFeatures, getParameterServerCount)
    val servers = Await
      .result[Array[ActorRef]](client.serverList(), timeout.duration)

    if (servers.isEmpty) {
      vector.destroy()
      throw new NoParameterServerException("Server List is Empty")
    } else if (getParameterServerCount * MinimumPsServerRatio > servers.size) {
      vector.destroy()
      throw new NotEnoughParameterServerException(s"Training Needs Parameter Server Count " +
        s": ${getParameterServerCount * MinimumPsServerRatio}, " +
        s"but now available parameter server count : ${servers.size}")
    } else if (getParameterServerCount * AcceptablePsServerRatio > servers.size) {
      logError("Training Needs Minimum Parameter Server Count : " +
        (getParameterServerCount * AcceptablePsServerRatio) +
        s" , now available parameter server count : ${servers.size} just fit the needs")
    }
    GlintPSClientHandler(vector)
  }


  /**
    * Records all Parameters for this training
    */
  def logParameters(): Unit = {
  }

  /** Train Jobs **/
  def trainRDD(dataset: RDD[(mutable.HashMap[Long, Double], Int)]): LogisticsRegressionModel = {
    logParameters()
    // xx
    if (IsAsynchronousAlgorithm) {
      trainAsyncRDD(dataset)
    } else {
      trainSyncRDD(dataset)
    }

    ???

  }

  private def splitTrainDataSet[T](dataset: RDD[T]): (RDD[T], RDD[T]) = {
    val Array(tests, trains) = dataset
      .randomSplit(Array(getSplitRatio, 1 - getSplitRatio))
    (trains, tests)
  }

  protected def trainAsyncRDD(dataset: RDD[(mutable.HashMap[Long, Double], Int)]): LogisticsRegressionModel = {
    type T = (mutable.HashMap[Long, Double], Int)
    val (trains: RDD[(mutable.HashMap[Long, Double], Int)], tests) = splitTrainDataSet[T](dataset)
    val pshandler = connect()

    var iter: Int = 0
    var lastLogLoss: Double = 0.0f
    var diffLogLoss: Double = 1.0f

    while (false == isFinishTraining(iter, diffLogLoss)) {
      iter = iter + 1
      var totalSamples: Long = 0L
      var totalFeatures: Long = 0L
      var totalCost: Double = 0.0f

      val records: RDD[(Long, Double, Double)] = trains.mapPartitions { iter =>

        iter.sliding(getBatchSize).map { data0 =>

          val data = mutable.Buffer(data0: _*)
          val keySet: Set[Long] = data
            .flatMap(_._1.keySet)
            .toSet
          val keys: Array[Long] = keySet
            .toArray
            .sorted
          data

          //          pshandler.PULL(keys) { w0 =>
          //            val weight: mutable.Map[Long, Double] = mutable.Map(keys.zip(w0).toSeq: _*)
          //
          //            val gradient: mutable.Map[Long, Double] = mutable.Map()
          //            var _features: Long = 0
          //            val cost = data map { case (vectorx, y1) =>
          //                _features = vectorx.
          //                val y0 = sigmod(weight, vectorx)
          //                val err = getEta(eta, iter) * (y0 - y1)
          //
          //                vectorx foreach { case ((j, xj)) if xj != 0.0 =>
          //                  weight += j -> (weight.getOrElse(j, 0.0) - err * xj)
          //                }
          //
          //                y1 * Math.log(y0) + (1 - y1) * Math.log(1 - y0)
          //            }.sum
          //          }
        }
        List((0L, 0.0, 0.0)).iterator
      }

      logInfo(s" Start Iteration $iter")
      logInfo(s" End Iteration $iter")
      logInfo(s" ---- Summary ----")
      logInfo(s"  Samples : $totalSamples")
      if (totalSamples > 0)
        logInfo(s"  Average Non Zero Feature Number : ${totalFeatures / totalSamples}")
      logInfo(s"  Total Cost : $totalCost")
      val logLoss = if (totalSamples > 0)
        totalCost / totalSamples
      else
        throw new Exception("Samples Set is Empty")
      diffLogLoss = (logLoss - lastLogLoss)
      lastLogLoss = logLoss

    }

    ???
  }

  def getCost(
               data: mutable.Buffer[(mutable.Map[Long, Double], Int)],
               weight: mutable.Map[Long, Double],
               eta: Double,
               iter: Int
             ): (Double, Double, mutable.Map[Long, Double]) = {
    data map { case (vector, y1) =>
      val features = vector.size
      val y0 = sigmod(weight, vector)
      val err = getEta(eta, iter) * (y0 - y1)
      y1 * Math.log(y0) + (1 - y1) * Math.log(1 - y0)
    }

    (0.0, 0L, mutable.Map.empty())
  }

  /**
    * Compute Learing Rate
    *
    * @param eta
    * @param iter
    * @return
    */
  def getEta(eta: Double, iter: Int): Double = {
    1 / math.sqrt(1.0 + LearningRateDecay * iter)
  }

  protected def isFinishTraining(iter: Int, diffLogLoss: Double): Boolean = {
    if (iter > (getMaxIter - 1)) {
      logInfo(s"Reach max Iteration(${getMaxIter}), Finish this Training")
      true
    } else if (Math.abs(diffLogLoss) < getTol) {
      logInfo(s"DiffLogLoss($diffLogLoss) < Tol(${getTol}), Finish this Training")
      true
    } else
      false
  }

  protected def trainSyncRDD(dataset: RDD[_]): LogisticsRegressionModel = {
    ???
  }

}

class LogisticsRegressionModel(
                                //                                override val vector: Vector,
                                val intercept: Double = 0.0f,
                                val numFeatures: Long,
                                val numClasses: Int = 2) {

}

object LogisticsRegressionModel extends MLReadable[LogisticsRegressionModel] {

  /**
    *
    * @return
    */
  def read: MLReader[LogisticsRegressionModel] = new LogisticRegressionModelReader

  /**
    *
    * @param path
    * @return
    */
  override def load(path: String): LogisticsRegressionModel = super.load(path)

  /**
    *
    * @param path
    * @param conf
    * @return
    */
  override def load(path: String, conf: Configuration): LogisticsRegressionModel = super.load(path, conf)

  private[LogisticsRegressionModel]
  class LogisticRegressionModelWriter(instance: LogisticsRegressionModel)
    extends MLWriter {

    private case class Date(
                             numClasses: Int,
                             numFeatures: Long,
                             isMultinomial: Boolean = false,
                             iteration: Int,
                             version: Int = 1,
                             diffLogLoss: Double = 0.0f,
                             lastLogLoss: Double = 0.0f)

    override protected def saveImpl(path: String): Unit = {
      // Default
    }

  }

  private[LogisticsRegressionModel]
  class LogisticRegressionModelReader extends MLReader[LogisticsRegressionModel] {
    /** checked against metadata when loading model **/
    private val className = classOf[LogisticsRegressionModel].getName

    /**
      *
      * @param path
      * @return
      */
    override def load(path: String): LogisticsRegressionModel = ???

    /**
      *
      * @param path
      * @param conf
      * @return
      */
    override def load(path: String, conf: Configuration): LogisticsRegressionModel = {
      ???
    }
  }

}

