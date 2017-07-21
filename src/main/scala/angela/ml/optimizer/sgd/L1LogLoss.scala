package angela.ml.optimizer.sgd

/**
  * Created by tuoyu on 20/07/2017.
  */
class L1LogLoss(override val lamda: Double)
  extends L1Loss with LogLoss {}

object L1LogLoss {
  def apply(lamda: Double): L1LogLoss = {
    new L1LogLoss(lamda)
  }
}
