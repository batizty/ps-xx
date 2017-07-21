package angela.ml.optimizer.sgd
import scala.collection.mutable

/**
  * Created by tuoyu on 20/07/2017.
  */
trait L1Loss extends Loss {

  protected def lamda: Double

  override def isL1Reg(): Boolean = true
  override def isL2Reg(): Boolean = false

  override def getReg(w: mutable.Map[Long, Double]): Double = {
    val reg = if (isL1Reg()) {
      w map { case (i, wi) => Math.abs(wi) } sum
    } else 0.0
    getRegParam() * reg
  }

  override def getRegParam(): Double = { this.lamda }
}
