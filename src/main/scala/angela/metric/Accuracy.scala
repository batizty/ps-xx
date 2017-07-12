package angela.metric

import org.apache.spark.rdd.RDD
/**
  * Created by tuoyu on 12/07/2017.
  */

object Accuracy {

  def compute(p: Double, y: Double)(implicit threshold: Double = 0.5) = {
    if (p > threshold)
      1
    else
      0
  }

  def of(rdd: RDD[(Double, Double)]) = {
    val (sum, size) = rdd.map {
      case (p, y) =>
        val v = compute(p, y)
        (v, 1)
    }.treeReduce {
      case ((v1, s1), (v2, s2)) =>
        (v1 + v2, s1 + s2)
    }
    sum.toDouble / size.toDouble
  }

}
