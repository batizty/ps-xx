package angela.utils

import org.scalatest.{FlatSpec, Matchers}

import scala.collection.mutable

/**
  * Created by tuoyu on 13/07/2017.
  */
class LibSvmParserSpec extends FlatSpec with Matchers {

  "Parser String" should "Get HashMap" in {
    val line = "1 1:1 10:2 300:1"
    val result = LibSvmParser.parse(line)
    val expect: (mutable.HashMap[Long, Double], Int) = {
      (mutable.HashMap(1L -> 1, 10L -> 2, 300L -> 1), 1)
    }
    result equals expect
  }

}
