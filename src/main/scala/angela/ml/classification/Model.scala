package angela.ml.classification

import org.apache.hadoop.conf.Configuration
import org.apache.spark.ml.util.{MLReader => SparkMLReader, MLWriter => SparkMLWriter}


/**
  * Created by tuoyu on 03/07/2017.
  * Base Model Trait
  * An Basic Model should with basic functions
  *   load
  */
trait MLReadable[T] {

  /**
    * Returns an [[MLReadable]] instance for this class
    */
  def read: MLReader[T]

  /**
    * Reads an ML instance from input path, a short for MLReader.load
    */
  def load(path: String): T = read.load(path)

  def load(path: String, conf: Configuration): T = read.load(path, conf)
}

trait MLWritable[T] {

  def write: MLWriter

  def save(path: String): Unit = write.save(path)
}

abstract class MLWriter extends SparkMLWriter {

  /**
    * Default setting overwrite option
    */
  override def overwrite(): MLWriter.this.type = {
    shouldOverwrite = true
    this
  }
}

abstract class MLReader[T] extends SparkMLReader {
  def load(path: String, conf: Configuration): T
}

