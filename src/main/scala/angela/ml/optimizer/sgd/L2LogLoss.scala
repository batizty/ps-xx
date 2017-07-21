package angela.ml.optimizer.sgd

import scala.collection.mutable.{Map => MMap}

/**
  * Created by tuoyu on 20/07/2017.
  * P(y = 0|x, w) = 1 / (1 + exp(w * x + b)
  * P(y = 1|x, w) = exp(w * x + b) / (1 + exp(w * x + b)
  *
  * l(w) = log(1 + exp(
  **/
class L2LogLoss(override val lamda: Double)
  extends L2Loss with LogLoss {}

object L2LogLoss {
  def apply(lamda: Double = 0.5): L2LogLoss = {
    new L2LogLoss(lamda)
  }
}