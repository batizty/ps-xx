package angela.ml.classfication

import angela.ml.classification.LogisticRegression
import angela.utils.LibSvmParser
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.SparkSession
import org.scalatest.{FlatSpec, Matchers}

import scala.collection.mutable

/**
  * Created by tuoyu on 11/07/2017.
  */

class LRTestSpec extends FlatSpec with Matchers {

  "A LR Test Running" should "Work fine" in {
    val output = "hdfs://10.77.100.41:9000/user/tuoyu/models/lr.asgd.model"
    val input = "hdfs://10.77.100.41:9000/user/tuoyu/data/a4a"

    val spark = SparkSession
      .builder
      .appName("LogisticsRegression")
      .master("local[*]")
      .getOrCreate()

    val data: RDD[(mutable.HashMap[Long, Double], Int)] = spark
      .sparkContext
      .textFile(input)
      .map(LibSvmParser.parse)

    val lr = new LogisticRegression("tuoyu")
      .setRegParam(0.5)
      .setMaxIteration(15)
      .setTol(1E-9)
      .setNumberOfFeatures(123)
      .setTrainDataSetSplitRatio(0.95)
      .setLearningRate(1)
      .setLearningRateDecay(0.5)
      .setModelPath(output)
      .setMetricStep(1)
      .setParameterServer(2)
      .setParameterMaster("h107710042.cluster.ds.weibo.com")
      .setBatchSize(10)
      .setAsyncAlgorithm(true)

    lr.trainRDD(data)
  }

}
