package angela.ml.optimizer.sgd
import scala.collection.mutable

/**
  * Created by tuoyu on 20/07/2017.
  */
trait L2Loss extends Loss {

  protected def lamda: Double

  override def isL2Reg(): Boolean = true
  override def isL1Reg(): Boolean = false

  override def getReg(w: mutable.Map[Long, Double]): Double = {
    val reg = if (isL2Reg()) {
      w map { case (i, wi) => wi * wi } sum
    } else 0.0
    0.5 * getRegParam() * reg
  }

  override def getRegParam(): Double = { this.lamda }
}
