package angela.utils


import scala.collection.mutable
import scala.util.Try
import scala.language.postfixOps

/**
  * Created by tuoyu on 13/07/2017.
  *
  * Parser For LibSvm Format Data
  * LibSvmFormat should be:
  * label key1:value1 key2:value2 key3:value3
  */
object LibSvmParser {

  def parse(line: String): (mutable.HashMap[Long, Double], Int) = {

    val s = line.split(" ")
    val label0: Int = Try {
      s(0).toFloat
    }.getOrElse(0.0f).toInt
    val label: Int = if (label0 == -1) 0 else label0

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
}
