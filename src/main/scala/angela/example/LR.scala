package angela.example

import angela.ml.classification.LogisticRegression
import angela.utils.LibSvmParser
import org.apache.spark.rdd.RDD
import org.apache.spark.{SparkConf, SparkContext}
import org.slf4j.LoggerFactory


/**
  * Created by tuoyu on 31/07/2017.
  */
object LR {

  val logger = LoggerFactory.getLogger(getClass.getName)

  def main(args: Array[String]): Unit = {

    logger.info(" Start LR ")

    val conf = new SparkConf()
    val sc = new SparkContext(conf)


    val output = "hdfs://10.77.100.41:9000/user/tuoyu/models/lr.asgd.model"
    val input = "hdfs://10.77.100.41:9000/user/tuoyu/data/a4a.t"

    val data: RDD[(scala.collection.mutable.HashMap[Long, Double], Int)] = sc
      .textFile(input)
      .map(LibSvmParser.parse)

    val lr = new LogisticRegression("tuoyu")
      .setRegParam(0.5)
      .setMaxIteration(100)
      .setTol(1E-9)
      .setNumberOfFeatures(124)
      .setTrainDataSetSplitRatio(0.95)
      .setLearningRate(1)
      .setLearningRateDecay(0.5)
      .setModelPath(output)
      .setMetricStep(1)
      .setParameterServer(4)
      .setParameterMaster("h107710044.cluster.ds.weibo.com")
      .setBatchSize(40)
      .setAsyncAlgorithm(true)
      .setElasticNetParam(0.001)

    lr.trainRDD(data)

    ()
  }
}
