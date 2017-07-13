package angela.utils


import scala.collection.mutable
import scala.util.Try
import scala.language.postfixOps

/**
  * Created by tuoyu on 13/07/2017.
  */
object LibSvmParser {

  def parse(line: String): (mutable.HashMap[Long, Double], Int) = {

    val s = line.split(" ")
    val label: Int = Try {
      s(0).toFloat
    }.getOrElse(0.0f).toInt

    val x = new mutable.HashMap[Long, Double]
    for (i <- 1 until s.length) {
      val xj = s(i).split(":")
      Try {
        xj(1).toDouble
      } foreach { value =>
        if (value != 0.0) {
          Try {
            xj(0).toLong
          } foreach { key =>
            if (value == Double.NaN
              || value == Double.NegativeInfinity
              || value == Double.PositiveInfinity) {
              throw new Exception(s"Found Not Valid Number, raw line = $line")
            }
            if (key >= 0) {
              x.put(Math.max(0, key), value)
            }
          }
        }
      }
    }

    (x, label)
  }

  def parseIntIndex(line: String): (mutable.HashMap[Int, Double], Int) = {
    val s = line.split(" ")
    val label: Int = Try {
      s(0).toFloat
    }.getOrElse(0.0f).toInt

    val x = new mutable.HashMap[Int, Double]
    for (i <- 1 until s.length) {
      val xj = s(i).split(":")
      Try {
        xj(1).toDouble
      } foreach { value =>
        if (value != 0.0) {
          Try {
            xj(0).toInt
          } foreach { key =>
            if (value == Double.NaN
              || value == Double.NegativeInfinity
              || value == Double.PositiveInfinity) {
              throw new Exception(s"Found Not Valid Number, raw line = $line")
            }
            if (key >= 0) {
              x.put(Math.max(0, key), value)
            }
          }
        }
      }
    }

    (x, label)
  }

  def getKV(s: String): Option[(Long, Double)] = {
    val arr = s.split(":")

    if (arr.length == 2) {
      Try {
        (arr(0).toLong, arr(1).toDouble)
      } toOption
    } else None
  }
}
