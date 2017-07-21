package angela.ml.optimizer.sgd

import scala.collection.mutable
import scala.collection.mutable.{Map => MMap}

/**
  * Created by tuoyu on 20/07/2017.
  */
trait LogLoss extends Loss {
  type T = MMap[Long, Double]

  /**
    * Calculate gradient of log loss function
    *
    * logloss(a, y) = log(1 + exp(-a * y), in which a = w dot x
    *
    * @param predication
    * @param label
    * @return
    */
  override def loss(predication: Double, label: Double): Double = {
    val z = predication * label
    if (z > 18) {
      Math.exp(-z)
    } else if (z < -18) {
      -z
    } else {
      Math.log(1 + Math.exp(-z))
    }
  }

  /**
    * Calculate gradient of log loss function
    *
    * d(logloss(a, y))/d(a) = y * exp(-a * y) / (1 + exp(-a * y))
    *
    * @param predication
    * @param label
    * @return
    */
  override def grad(predication: Double, label: Double): Double = {
    val z = predication * label
    if (z > 18) {
      label * Math.exp(-z)
    } else if (z < -18) {
      label
    } else {
      label / (1.0 + Math.exp(z))
    }
  }

  /**
    * Predict the label of sample
    *
    * @param w
    * @param x
    * @return
    */
  override def predict(w: T, x: T): Double = {
    x map { case (i, xi) if xi != 0.0 => w.getOrElse(i, 0.0) * xi } sum
  }


  def loss(w: T, x: T, y: Double): Double = {
    loss(predict(w, x), y)
  }

  def loss(w: T, data: Array[(T, Double)]): Double = {
    data.map { case (x, y) =>
      loss(w, x, y)
    }.sum + getReg(w)
  }
}
