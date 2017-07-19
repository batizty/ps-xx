package angela.core

import org.apache.hadoop.conf.Configuration

/**
  * Created by tuoyu on 04/07/2017.
  */
trait PSClientHandler[T] {

  def PULL(keys: Array[Long])(f: Array[T] => Unit)
  def PUSH(keys: Array[Long], values: Array[T])(f: Boolean => Unit)
  def SAVE(path: String, conf: Configuration)(f: Boolean => Unit)
//  def INIT(): PSClientHandler[T]
  def DESTROY()(f: Boolean => Unit)
}