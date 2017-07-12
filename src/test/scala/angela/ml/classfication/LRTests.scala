package angela.ml.classfication

import org.apache.spark.sql.SparkSession
import angela.ml.classification.LogisticRegression

/**
  * Created by tuoyu on 11/07/2017.
  */
object LRTests {
  val spark = SparkSession
    .builder
    .appName("LogisticsRegression")
    .master("local[*]")
    .getOrCreate()

  val lr = new LogisticRegression("1")
    .setRegParam(0.5)
    .setMaxIteration(10)
    .setTol(1E-4)
    .setElasticNetParam(1.0)
    .setParameterServer(4)
    .setParameterMaster("10.77.100.41")
}
