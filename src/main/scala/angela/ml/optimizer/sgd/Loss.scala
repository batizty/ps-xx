package angela.ml.optimizer.sgd

/**
  * Created by tuoyu on 20/07/2017.
  */
import scala.collection.mutable.Map

trait Loss {
  def loss(predication: Double, label: Double): Double
  def grad(predication: Double, label: Double): Double
  def predict(w: Map[Long, Double], x: Map[Long, Double]): Double

  def isL2Reg(): Boolean
  def isL1Reg(): Boolean
  def getRegParam(): Double
  def getReg(w: Map[Long, Double]): Double
}
