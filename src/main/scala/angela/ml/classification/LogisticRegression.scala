package angela.ml.classification

import akka.actor.ActorRef
import akka.util.Timeout
import angela.core.{GlintPSClientHandler, PSClientHandler}
import angela.exception._
import angela.metric.Accuracy
import angela.utils.{FileUtils, Logging}
import com.typesafe.config.ConfigFactory
import glint.Client
import org.apache.hadoop.conf.Configuration
import org.apache.spark.ml.param.ParamMap
import org.apache.spark.ml.util.Identifiable
import org.apache.spark.mllib.evaluation.BinaryClassificationMetrics
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.Dataset

import scala.annotation.tailrec
import scala.collection.mutable
import scala.collection.mutable.ListBuffer
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext}
import scala.language.implicitConversions
import scala.language.postfixOps


/**
  * Logistic regression. Supports binomial logistic regression with Parameter Server Support.
  *
  * * Features
  *  1. L1, L2
  *  2. SSP, ASGD
  *  3. split implementation
  *  4. connect multiply ps interface
  *  5. min max threshold -- Done
  *  6. tiny test data set -- Done
  *  7. read model
  *  8. add Parameters Setting -- Done
  *  9. save model -- Done
  *  10. record parameters-- Done
  *  11. tiny verify dataset -- Done
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
    with MetricStep
    with ModelPath {

  def setThreshold(value: Double): this.type = {
    assert(value < 1.0f && value > 0.0, "Threshold should be in (0.0, 1.0)")
    if (isSet(threshold)) clear(threshold)
    set(threshold, value)
  }
}

object LogisticRegression extends Logging {

  implicit def Long2Int(x: Long): Int = {
    try {
      x toInt
    } catch {
      case err: Throwable =>
        logError(s"convert Long2Int Failed $x", err)
        throw err
    }
  }

  implicit def Map2MutableMap[V, T](m: Map[V, T]): mutable.Map[V, T] = mutable.Map[V, T](m.toSeq: _*)
}

class LogisticRegression(val uid: String)
  extends LogisticRegressionParams with Logging {
  val MinimumPsServerRatio: Double = 0.5f
  val AcceptablePsServerRatio: Double = 0.8f
  val MAX_TEST_DATA_SET_COUNT: Long = 1000000L

  def GRADIENT_MINIMUM_THRESHOLD: Double = getTol * 1E-4

  def this() = this(Identifiable.randomUID("logreg-withps-asgd"))

  /** Setting up Parameters **/

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
    * Set Max Iteration Times.
    * Default is 20.
    */
  def setMaxIteration(value: Int): this.type = set(maxIter, value)

  setDefault(maxIter -> 20)


  /**
    * Whether to fit an intercept term
    * Default is true
    *
    * @group setParam
    */
  def setFitIntercept(value: Boolean): this.type = set(fitIntercept, value)

  setDefault(fitIntercept -> true)

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
    * This this the lamda in alpha * (lamda * ||w||) + (1 - alpha) * ((lamda / 2) * (Wt * W)),
    *
    * @group setParam
    */
  def setElasticNetParam(value: Double): this.type = set(elasticNetParam, value)

  setDefault(elasticNetParam -> 1.0)

  /**
    * Set parameter server count for this trainning work
    *
    * @group setParam
    */
  def setParameterServer(value: Int): this.type = set(parameterServerCount, value)

  /**
    * Set parameter master address
    *
    * @group setParam
    */
  def setParameterMaster(value: String): this.type = set(parameterServerMaster, value)

  /**
    * Set Threshold for this Model
    *
    * @group setThreshold
    */
  override def setThreshold(value: Double): this.type = super.setThreshold(value)

  override def getThreshold: Double = super.getThreshold

  /**
    * Set Number of Features of Training
    *
    * @group setParam
    */
  def setNumberOfFeatures(value: Long): this.type = set(numOfFeatures, value)

  /**
    * Choose Async Algorithm or not
    *
    * @group setParam
    */
  def setAsyncAlgorithm(value: Boolean): this.type = set(isAsynchronousAlgorithm, value)

  setDefault(isAsynchronousAlgorithm -> true)

  /**
    * Set ASGD Batch Size to pull/push gradient
    * Default 1000
    *
    * @group setParam
    */
  def setBatchSize(value: Int): this.type = set(batchSize, value)

  /**
    * Split ratio for Training Data
    * One part will be used as Training Data
    * Last part will be used to as a tiny Verify Data Set
    *
    * Default, 99% data will be used as Training Data
    *
    * @group setParam
    */
  def setTrainDataSetSplitRatio(value: Double): this.type = set(splitRatio, value)

  setDefault(splitRatio -> 0.99)

  /**
    * Init Learning Rate for SGD Algorithm
    * LearningRate = initLearningRate /  (1.0 + getLearningRateDecay * iter)
    * Default 1.0
    *
    * @group setParam
    */
  def setLearningRate(value: Double): this.type = set(learningRate, value)

  /**
    * Learning Rate Decay for SGD Algorithm
    *
    * @group setParam
    */
  def setLearningRateDecay(value: Double): this.type = set(learningRateDecay, value)

  setDefault(learningRateDecay -> 0.5)

  /**
    * ModelPath output file address(hdfs)
    *
    * @group setParam
    */
  def setModelPath(value: String): this.type = set(path, value)

  /**
    * Metric Step, every step for metric developing
    *
    * @group setParam
    */
  def setMetricStep(value: Int): this.type = set(metricStep, value)

  /** ------- Finish Parameters -------- **/

  override def copy(extra: ParamMap): LogisticRegression = defaultCopy(extra)

  // implicit values
  implicit val timeout: Timeout = 120 seconds
  implicit val ec: ExecutionContext = ExecutionContext.Implicits.global

  def train(dataset: Dataset[_], handlePersistence: Boolean): LogisticRegression = {
    // TODO support Spark Standard interface
    ???
  }

  /**
    * Default intercept 0.0
    */
  private val intercept: Double = 0.0

  /**
    * Sigmoid Function : f(X) = 1 / (1 + exp(- Wt * X + b) = y
    *
    * @param weight Vector W
    * @param x      Vector x
    * @return y
    */
  private def sigmoid(weight: mutable.Map[Long, Double], x: mutable.Map[Long, Double]): Double = {
    val margin: Double = x.filterNot(_._2 == 0.0f)
      .map { case (index, value) =>
        weight.getOrElse(index, 0.0) * value
      }.sum + intercept
    if (margin <= -23) {
      _min
    } else if (margin >= 20) {
      _max
    } else
      1.0 / (1.0 + math.exp(-margin))
  }

  @inline
  private def _min: Double = 1.0 / (1.0 + 1E-10)

  @inline
  private def _max: Double = 1.0 / (1.0 + 1E9)

  /**
    * Init Parameter Server Connection and setting up model parameters which are related with
    * model, it is preparation for later training
    * TODO parameter should could init Parameter Server Client Handler
    * TODO Move this part to PSClient
    */
  def connect(): PSClientHandler[Double] = {
    /**
      * Later Add Detail Configurations
      */
    val config = ConfigFactory
      .parseString(s"glint.master.host=" + getMaster)

    val client = Client(config)
    val vector = client
      .vectorWithCyclicPartitioner[Double](getNumOfFeatures, getParameterServerCount)
    val servers = Await
      .result[Array[ActorRef]](client.serverList(), timeout.duration)

    if (servers.isEmpty) {
      vector.destroy()
      throw NoParameterServerException("Server List is Empty")
    } else if (getParameterServerCount * MinimumPsServerRatio > servers.length) {
      vector.destroy()
      throw NotEnoughParameterServerException(s"Training Needs Parameter Server Count " +
        s": ${getParameterServerCount * MinimumPsServerRatio}, " +
        s"but now available parameter server count : ${servers.length}")
    } else if (getParameterServerCount * AcceptablePsServerRatio > servers.length) {
      logError("Training Needs Minimum Parameter Server Count : " +
        (getParameterServerCount * AcceptablePsServerRatio) +
        s" , now available parameter server count : ${servers.length} just fit the needs")
    }
    GlintPSClientHandler(vector)
  }


  /**
    * Records all Parameters for this training
    */
  def logParameters(): Unit = {
    log.info("")
    log.info("> Training Parameters <")
    log.info(" >> LR Parameters <<")
    log.info(" RegParam           : " + getRegParam)
    log.info(" MaxIteration       : " + getMaxIter)
    log.info(" FitItercept        : " + getFitIntercept)
    log.info(" Tol                : " + getTol)
    log.info(" ElasticNetParam    : " + getElasticNetParam)
    log.info(" Threshold          : " + getThreshold)
    log.info(" NumOfFeatures      : " + getNumOfFeatures)
    log.info(" isAsynAlgorithm    : " + IsAsynchronousAlgorithm)
    log.info(" TrainSplitRatio    : " + getSplitRatio)
    log.info(" LearningRate       : " + getInitLearningRate)
    log.info(" LearningReteDecay  : " + getLearningRateDecay)
    log.info(" Model Path         : " + getModelPath)
    log.info(" Metric Step        : " + getMetricStep)
    log.info("")
    log.info(" >> PS Parameters << ")
    log.info(" ParameterServerCount : " + getParameterServerCount)
    log.info(" ParameterMaster    :" + getMaster)
    log.info(" BatchSize          : " + getBatchSize)
    log.info("")
  }

  /** Train Jobs **/
  def trainRDD(dataset: RDD[(mutable.HashMap[Long, Double], Int)]): LogisticsRegressionModel = {
    logParameters()

    val pshandler = connect()

    if (IsAsynchronousAlgorithm) {
      trainASGDRDD(dataset, pshandler)
    } else {
      trainSSPRDD(dataset, pshandler)
    }
  }

  /**
    * Split DataSet to Train DataSet and a tiny Verify DataSet, and Verify DataSet
    * should be smaller than 1m
    *
    * @return RDD[T]
    */
  private def splitTrainDataSet[T](dataset: RDD[T]): (RDD[T], RDD[T]) = {
    val Array(trains, test) = dataset
      .randomSplit(Array(getSplitRatio, 1 - getSplitRatio))

    @tailrec
    def loop(data: RDD[T]): RDD[T] = {
      val count = data.count()
      if (count > MAX_TEST_DATA_SET_COUNT) {
        val Array(_, x) = data.randomSplit(Array(getSplitRatio, 1 - getSplitRatio))
        loop(x)
      } else data
    }

    (trains, loop(test))
  }

  protected def trainSSPRDD(
                             trains: RDD[(mutable.HashMap[Long, Double], Int)],
                             psHandler: PSClientHandler[Double]
                           ): LogisticsRegressionModel = {

    var iterationTime: Int = 0
    var lastLogLoss: Double = 0.0f
    var diffLogLoss: Double = 1.0f

    logInfo(s" ---> Start Iteration $iterationTime <---")

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
          psHandler.PULL(keys) { w0 =>
            val vectorW0: mutable.Map[Long, Double] = mutable.Map(keys.zip(w0).toSeq: _*)
            val (cost: Double, features: Long, vectorW) = costFunc(data, vectorW0, iterationTime)

            rddTotalCost = -cost

            val grad = getGradient(vectorW0, vectorW, iterationTime, data.size)
              .filter(_._2 > GRADIENT_MINIMUM_THRESHOLD)
              .toArray
              .sortBy(_._1)
            // 可能需要多次推送
            psHandler.PUSH(grad.map(_._1), grad.map(_._2)) { result =>
              logInfo(s"PUSH Operation result = $result")
            }
          }
        }

        Iterable((rddTotalSamples, rddTotalFeatures, rddTotalCost)).iterator
      } reduce { case (a, b) => (a._1 + b._1, a._2 + b._2, a._3 + b._3) }

    ???
  }

  protected def trainASGDRDD(
                              dataset: RDD[(mutable.HashMap[Long, Double], Int)],
                              psHandler: PSClientHandler[Double]
                            ): LogisticsRegressionModel = {
    type T = (mutable.HashMap[Long, Double], Int)
    val (trains: RDD[T], tests: RDD[T]) = splitTrainDataSet[T](dataset)

    var iterationTime: Int = 0
    var lastLogLoss: Double = 0.0f
    var diffLogLoss: Double = 1.0f

    logInfo(s" ---> Start Iteration $iterationTime <---")

    var model: Option[LogisticsRegressionModel] = None

    while (!isFinishTraining(iterationTime, diffLogLoss)) {
      iterationTime = iterationTime + 1

      val (totalSamples: Long, totalFeatures: Long, totalCost: Double) = trains
        .mapPartitions { dataPartition =>
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

      logInfo(s" ---> End Iteration $iterationTime <---")
      logInfo(s" > Train Summary <")
      logInfo(s"\tSamples : " + totalSamples)
      if (totalSamples > 0) {
        logInfo(s"\tNot Zero Average Features: " + (totalFeatures.toDouble / totalSamples.toDouble))
      }
      logInfo(s"\tCost : " + totalCost)

      val logLoss = if (totalSamples > 0)
        totalCost / totalSamples
      else
        throw new ParameterServerException("Training Sample Size is Zero")
      diffLogLoss = logLoss - lastLogLoss
      lastLogLoss = logLoss

      model = Some(generateLogisticModel(diffLogLoss, iterationTime, totalSamples, lastLogLoss, psHandler))
      if (model.isDefined &&
        !tests.isEmpty() &&
        (iterationTime / getMetricStep == 0))
        verifyWithTinyTests(tests, psHandler)
    }

    model match {
      case Some(m) => m
      case None => throw new Exception(" No Module is Generated ")
    }
  }

  private def verifyWithTinyTests(
                                   tests: RDD[(mutable.HashMap[Long, Double], Int)],
                                   psHandler: PSClientHandler[Double]
                                 ): Unit = {
    val predicationAndLable: RDD[(Double, Double)] = predict(tests, psHandler)
      .map(x => (x._1, x._2.toDouble))
    val metrics = new BinaryClassificationMetrics(predicationAndLable)
    logInfo(" > Binary Classification Metrics <")

    val auPRC = metrics.areaUnderPR
    logInfo("  Area under precision-recall curve : " + auPRC)

    // AUROC
    val auROC = metrics.areaUnderROC
    logInfo(" Area under ROC : " + auROC)

    // ACC
    val acc = Accuracy.of(predicationAndLable)
    logInfo(" Accuracy : " + acc)
  }

  def predict(
               tests: RDD[(mutable.HashMap[Long, Double], Int)],
               psHandler: PSClientHandler[Double]
             ): RDD[(Double, Int)] = {
    tests mapPartitions { dataPartitions =>
      val predictionAndLabel: mutable.ListBuffer[(Double, Int)] = new ListBuffer[(Double, Int)]()

      dataPartitions.sliding(getBatchSize) foreach { rawData =>
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

            val predicts: List[(Double, Int)] = data map {
              case (vectorX, y1) =>
                (sigmoid(vectorW0, vectorX), y1)
            } toList

            predictionAndLabel ++= predicts
        }
      }
      predictionAndLabel.iterator
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
        val y0 = sigmoid(vectorW, vectorX)
        val err = getLearningRate(iterationTime) * (y0 - y1)

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
    * L1 Regular, Not support now
    *
    * @return grad after with L1 Penalty
    */
  def L1Reg: mutable.Map[Long, Double] => mutable.Map[Long, Double] = {
    grad => grad
  }

  /**
    * L2 Regular
    *
    * @return grad after with L2 Penalty
    */
  def L2Reg: (mutable.Map[Long, Double], mutable.Map[Long, Double], Int) => mutable.Map[Long, Double] = {
    (weight, grad, iteration) =>
      if (getRegParam != 1.0) {
        grad foreach {
          case (i, wi) if weight.contains(i) =>
            val v = (1.0 - getRegParam) * getLearningRate(iteration) * weight(i)
            grad += i -> v
        }
      }
      grad
  }

  /**
    * Compute learning rate through iteration and init learing rate with decay
    * lr = lr_0 / math.sqrt(1.0 + decay * iteration)
    *
    * @param iteration Iteration Times
    */
  def getLearningRate(iteration: Int): Double = {
    getInitLearningRate / Math.sqrt(1.0 + getLearningRateDecay * iteration)
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
          _file_handler flush()
        }
      }

      setModelPath(path, conf)
    }
    // TODO chang to return True Or False
    true
  }
}

