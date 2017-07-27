package angela.math

import scala.collection.mutable
/**
  * Created by tuoyu on 20/07/2017.
  */

/**
object GDoubleVector extends GVector[Double] {
  override def dot(x: T, y: T): Double = {
    0.0
  }

  override def apxy(a: Double, x:T, y: T): Unit = super.apxy(a, x, y)

  override def zero: Double = 0.0
}

trait GVector[V] {
  type T = mutable.Map[Long, V]
  def dot(x: T, y: T): V

  def apxy(a: V, x:T, y: T): Unit = {
    x foreach { case (i, xi) =>
      y += (i -> (y.getOrElse(i, zero) + a * 1))
    }
  }

  def zero: V
} **/
