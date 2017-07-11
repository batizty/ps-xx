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
import angela.utils.{FileUtils, Logging}
import angela.exception._

/**
  * Logistic regression. Supports multinomial logistic (softmax) regression and binomial logistic
  * regression.
  *
  * TODO
  * 1 搞定L1正则 -- TODO
  * 2 拆开cost函数 -- DONE
  * 3 实现ssp asp
  * 4 拆开实现逻辑
  * 5 链接上ps接口
  * 6 加上min max threshold
  * 7 加上测试auc logdiff的代码
  * 8
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
    with TrainDataSetSplitRatio
    with LearningRate
    with LearningRateDecay
    with ModelPath {

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
    * Set the ElasticNet mixing parameter.
    * For alpha = 0, the penalty is an L2 penalty. For alpha = 1, it is an L1 penalty.
    * For 0 < alpha < 1, the penalty is a combination of L1 and L2.
    * Default is 0.0 which is an L2 penalty.
    *
    * @group setParam
    */
  def setRegParam(value: Double): this.type = set(regParam, value)

  setDefault(regParam -> 0.0)

  /**
    * This this the lamda in alpha * (lamda * ||w||) + (1 - alpha) * ((lamda / 2) * (Wt * W)),
    *
    * @param value
    * @return
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
    log.info("----- Parameters -----")
    log.info("----------------------")
  }

  /** Train Jobs **/
  def trainRDD(dataset: RDD[(mutable.HashMap[Long, Double], Int)]): LogisticsRegressionModel = {
    logParameters() // TODO
    type T = (mutable.HashMap[Long, Double], Int)

    val (trains: RDD[T], tests) = splitTrainDataSet[T](dataset)
    val pshandler = connect()

    if (IsAsynchronousAlgorithm) {
      trainASGDRDD(dataset, pshandler)
    } else {
      trainSSPRDD(dataset, pshandler)
    }
    //
    //    var iterationTime: Int = 0
    //    var lastLogLoss: Double = 0.0f
    //    var diffLogLoss: Double = 1.0f
    //
    //    while (false == isFinishTraining(iterationTime, diffLogLoss)) {
    //      iterationTime = iterationTime + 1
    //
    //        if (IsAsynchronousAlgorithm) {
    //          // TODO refactor to inject code
    //          trainASGDRDD(dataset, pshandler)
    //        } else {
    //          trainSSPRDD(dataset, pshandler)
    //        }
    //
    //      logInfo(s" Start Iteration $iterationTime")
    //      logInfo(s" End Iteration $iterationTime")
    //      logInfo(s" ---- Summary ----")
    //      logInfo(s"  Samples : $totalSamples")
    //      if (totalSamples > 0)
    //        logInfo(s"  Average Non Zero Feature Number : ${totalFeatures.toDouble / totalSamples.toDouble}")
    //      logInfo(s"  Total Cost : $totalCost")
    //
    //      val logLoss = if (totalSamples > 0)
    //        totalCost / totalSamples
    //      else
    //        throw new Exception("Samples Set is Empty")
    //      diffLogLoss = (logLoss - lastLogLoss)
    //      lastLogLoss = logLoss

    // TODO 加上测试的代码

    //    }

    // TODO add LogisticRegression Model
    ???
  }

  private def splitTrainDataSet[T](dataset: RDD[T]): (RDD[T], RDD[T]) = {
    val Array(tests, trains) = dataset
      .randomSplit(Array(getSplitRatio, 1 - getSplitRatio))
    (trains, tests)
  }

  protected def trainSSPRDD(
                             trains: RDD[(mutable.HashMap[Long, Double], Int)],
                             pshandler: PSClientHandler[Double]
                           ): LogisticsRegressionModel = {

    var iterationTime: Int = 0
    var lastLogLoss: Double = 0.0f
    var diffLogLoss: Double = 1.0f

    val (totalSamples: Long, totalFeatures: Long, totalCost: Double) = trains
      .mapPartitions { dataPartition =>
        val rddTotalSamples = dataPartition.size toLong

        val data = mutable.Buffer(dataPartition.toArray: _*)
        val keySet: Set[Long] = data
          .flatMap(_._1.keySet)
          .toSet
        val keys: Array[Long] = keySet
          .toArray
          .sorted

        val rddTotalFeatures: Long = keys.size
        var rddTotalCost: Double = 0.0

        while (isFinishTraining(iterationTime, diffLogLoss)) {
          iterationTime = iterationTime + 1
          // 可能需要多次拉取
          pshandler.PULL(keys) { w0 =>
            val vectorW0: mutable.Map[Long, Double] = mutable.Map(keys.zip(w0).toSeq: _*)
            val (cost: Double, features: Long, vectorW) = costFunc(data, vectorW0, iterationTime)

            rddTotalCost = -cost

            val grad = getGradient(vectorW0, vectorW, iterationTime, data.size)
              .toArray
              .sortBy(_._1)
            // 可能需要多次推送
            pshandler.PUSH(grad.map(_._1), grad.map(_._2)) { result =>
              logInfo(s"PUSH Operation result = $result")
            }
          }
        }

        Iterable((rddTotalSamples, rddTotalFeatures, rddTotalCost)).iterator
      } reduce { case (a, b) => (a._1 + b._1, a._2 + b._2, a._3 + b._3) }

    logInfo(s" Start Iteration $iterationTime")
    logInfo(s" End Iteration $iterationTime")
    logInfo(s" ---- Summary ----")
    logInfo(s"  Samples : $totalSamples")
    if (totalSamples > 0)
      logInfo(s"  Average Non Zero Feature Number : ${totalFeatures.toDouble / totalSamples.toDouble}")
    logInfo(s"  Total Cost : $totalCost")

    val logLoss = if (totalSamples > 0)
      totalCost / totalSamples
    else
      throw new Exception("Samples Set is Empty")
    diffLogLoss = (logLoss - lastLogLoss)
    lastLogLoss = logLoss

    ???
  }


  protected def trainASGDRDD(
                              trains: RDD[(mutable.HashMap[Long, Double], Int)],
                              psHandler: PSClientHandler[Double]
                            ): LogisticsRegressionModel = {

    var iterationTime: Int = 0
    var lastLogLoss: Double = 0.0f
    var diffLogLoss: Double = 1.0f

    var model: Option[LogisticsRegressionModel] = None

    while (false == isFinishTraining(iterationTime, diffLogLoss)) {
      iterationTime = iterationTime + 1

      val (totalSamples: Long, totalFeatures: Long, totalCost: Double) = trains
        .mapPartitions {
          dataPartition =>
            var rddTotalCost: Double = 0.0
            var rddTotalSamples: Long = 0L
            var rddTotalFeatures: Long = 0L

            dataPartition.sliding(getBatchSize) foreach {
              rawData =>
                val data = mutable.Buffer(rawData: _*)
                val keySet: Set[Long] = data
                  .flatMap(_._1.keySet)
                  .toSet
                val keys: Array[Long] = keySet
                  .toArray
                  .sorted

                psHandler.PULL(keys) {
                  w0 =>
                    val vectorW0: mutable.Map[Long, Double] = mutable.Map(keys.zip(w0).toSeq: _*)
                    val (cost: Double, features: Long, vectorW) = costFunc(data, vectorW0, iterationTime)

                    rddTotalCost = -cost
                    rddTotalSamples += data.size
                    rddTotalFeatures += features

                    val grad = getGradient(vectorW0, vectorW, iterationTime, data.size)
                      .toArray
                      .sortBy(_._1)
                    psHandler.PUSH(grad.map(_._1), grad.map(_._2)) {
                      result =>
                        logInfo(s"PUSH Operation result = $result")
                    }
                }
            }

            Iterable((rddTotalSamples, rddTotalFeatures, rddTotalCost)).iterator
        } reduce { case (a, b) => (a._1 + b._1, a._2 + b._2, a._3 + b._3) }

      logInfo(s" Start Iteration $iterationTime")
      logInfo(s" End Iteration $iterationTime")
      logInfo(s" ---- Summary ----")
      logInfo(s"  Samples : $totalSamples")
      if (totalSamples > 0)
        logInfo(s"  Average Non Zero Feature Number : ${
          totalFeatures.toDouble / totalSamples.toDouble
        }")
      logInfo(s"  Total Cost : $totalCost")

      val logLoss = if (totalSamples > 0)
        totalCost / totalSamples
      else
        throw new Exception("Samples Set is Empty")
      diffLogLoss = (logLoss - lastLogLoss)
      lastLogLoss = logLoss

      model = Some(generateLogisticModel(diffLogLoss, iterationTime, totalSamples, lastLogLoss, psHandler))
      if (model.nonEmpty) {
        // TODO do test set run

      }
    }
    model match {
      case Some(m) => m
      case None => throw new Exception(" No Module is Generated ")
    }
  }

  def costFunc(
                trainData: mutable.Buffer[(mutable.HashMap[Long, Double], Int)],
                vectorWeight: mutable.Map[Long, Double],
                iterationTime: Int
              ): (Double, Long, mutable.Map[Long, Double]) = {
    val vectorW: mutable.Map[Long, Double] = vectorWeight

    val (cost: Double, features: Long) = trainData map {
      case (vectorX, y1) =>
        val y0 = sigmod(vectorW, vectorX)
        val err = learningRate(iterationTime) * (y0 - y1)

        vectorX foreach {
          case ((j, xj)) if xj != 0.0 =>
            vectorW += j -> (vectorW.getOrElse(j, 0.0) - err * xj)
        }
        val _cost = y1 * Math.log(y0) + (1 - y1) * Math.log(1 - y0)
        (_cost, vectorX.size.toLong)
    } reduce ((a, b) => (a._1 + b._1, a._2 + b._2))

    (cost, features, vectorW)
  }

  def getGradient(
                   vectorWOld: mutable.Map[Long, Double],
                   vectorW: mutable.Map[Long, Double],
                   batchSize: Int,
                   iteration: Int
                 ): mutable.Map[Long, Double] = {
    val grad: mutable.Map[Long, Double] = mutable.Map.empty

    // Without L1 Reg and L2 Reg
    (vectorW.keySet union vectorWOld.keySet) foreach {
      key =>
        grad += key -> (vectorW.getOrElse(key, 0.0) - vectorWOld.getOrElse(key, 0.0)) / batchSize.toDouble
    }

    val gradL1 = if (getRegParam > 0.0) {
      // L1 Reg
      L1Reg(grad)
    } else grad

    val gradL2 = if (getRegParam < 1.0) {
      // L2 Reg
      L2Reg(vectorW, gradL1, iteration)
    } else gradL1

    gradL2
  }

  /**
    * L1
    *
    * TODO
    *
    * @return
    */
  def L1Reg: mutable.Map[Long, Double] => mutable.Map[Long, Double] = {
    grad =>
      grad
  }

  /**
    * L2 Reg
    *
    * @return
    */
  def L2Reg: (mutable.Map[Long, Double], mutable.Map[Long, Double], Int) => mutable.Map[Long, Double] = {
    (weight, grad, iteration) =>
      if (getRegParam != 1.0) {
        grad foreach {
          case (i, wi) if weight.contains(i) =>
            grad += i -> (1.0 - getRegParam) * learningRate(iteration) * weight(i)
        }
      }
      grad
  }

  def learningRate(iter: Int): Double = {
    getLearningRate / (1.0 + getLearningRateDecay * iter)
  }

  protected def isFinishTraining(iter: Int, diffLogLoss: Double): Boolean = {
    if (iter > (getMaxIter - 1)) {
      logInfo(s"Reach max Iteration(${
        getMaxIter
      }), Finish this Training")
      true
    } else if (Math.abs(diffLogLoss) < getTol) {
      logInfo(s"DiffLogLoss($diffLogLoss) < Tol(${
        getTol
      }), Finish this Training")
      true
    } else
      false
  }

  def generateLogisticModel(
                             diffLogLoss: Double,
                             iteration: Int,
                             samples: Long,
                             lastLogLoss: Double,
                             psHandler: PSClientHandler[Double]
                           ): LogisticsRegressionModel = {
    val model = new LogisticsRegressionModel(
      intercept = intercept,
      numFeatures = getNumOfFeatures,
      numClasses = 2,
      diffLogLoss = Some(diffLogLoss),
      lastLogLoss = Some(lastLogLoss),
      samples = Some(samples),
      iteration = Some(iteration))

    val conf = new Configuration()
    val path = if (isFinishTraining(iteration, diffLogLoss))
      getModelPath
    else
      List(getModelPath, iteration).mkString("--")
    if (model.saveModel(psHandler, path, conf)) {
      logInfo(s"Store Model into $path OK")
    } else {
      logError(s"Failed to Store Model into $path")
    }
    model
  }
}

class LogisticsRegressionModel(
                                val intercept: Double = 0.0f,
                                val numFeatures: Long,
                                val numClasses: Int = 2,
                                val diffLogLoss: Option[Double] = None,
                                val lastLogLoss: Option[Double] = None,
                                val samples: Option[Long] = None,
                                val iteration: Option[Int] = None
                              ) {
  var path: Option[String] = None
  var conf: Option[Configuration] = None

  val version: String = "0.1"

  def setModelPath(p: String, c: Configuration): Unit = {
    path = Some(p)
    conf = Some(c)
  }

  def header: String = {
    var header = List("version " + version,
      "solver_type " + "L2R_LR",
      "nr_class " + numClasses,
      "label " + "0 1",
      "nr_feature " + numFeatures,
      "bias " + intercept,
      "type fold")
    diffLogLoss foreach { dll =>
      header = header ++ List("diffLogLoss " + dll)
    }
    lastLogLoss foreach { lll =>
      header = header ++ List("lastLogLoss " + lastLogLoss)
    }
    samples foreach { s =>
      header = header ++ List("samples " + samples)
    }
    iteration foreach { i =>
      header = header ++ List("iteration " + iteration)
    }
    header = header ++ List("w")
    header.mkString("\n")
  }

  def saveModel(psHandler: PSClientHandler[Double], path: String, conf: Configuration) = {
    psHandler.SAVE(path, conf) { ret =>
      println(s"Save Model into $path with Result $ret")
      FileUtils.initHDFSFile(path + "part-0", conf) { _hdfs_file =>
        FileUtils.printToFile(_hdfs_file) { _file_handler =>
          _file_handler println header
          _file_handler flush
        }
      }

      setModelPath(path, conf)
    }
    // TODO chang to return True Or False
    true
  }

}
/**
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
                             intercept: Double,
                             numFeatures: Long,
                             numClasses: Int = 2,
                             diffLogLoss: Option[Double] = None,
                             lastLogLoss: Option[Double] = None,
                             samples: Option[Long] = None,
                             iteration: Option[Int] = None
                           )

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
  **/
