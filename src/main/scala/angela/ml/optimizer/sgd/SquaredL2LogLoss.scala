package angela.ml.optimizer.sgd
import scala.collection.mutable

/**
  * Created by tuoyu on 21/07/2017.
  */
class SquaredL2LogLoss(override val lamda: Double) extends L2Loss {

  override def loss(predication: Double, label: Double): Double = {
    if (label > 0)
      Math.log1p(Math.exp(predication))
    else
      Math.log1p(Math.exp(predication)) - predication
  }
  override def grad(predication: Double, label: Double): Double = {
    val pre = 1.0 / (1.0 + Math.exp(predication))
    (label - pre)
  }

  override def predict(w: mutable.Map[Long, Double], x: mutable.Map[Long, Double]): Double = {
    val margin: Double = x.map { case (i, xi) => xi * w.getOrElse(i, 0.0) }.sum
    -1.0 * margin
  }
}

object SquaredL2LogLoss {
  def apply(lamda: Double): SquaredL2LogLoss = {
    new SquaredL2LogLoss(lamda)
  }
}
