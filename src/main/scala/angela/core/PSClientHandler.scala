package angela.core

/**
  * Created by tuoyu on 04/07/2017.
  */
trait PSClientHandler[T] {

  def PULL(keys: Array[Long])(f: Array[T] => Unit)
  def PUSH(keys: Array[Long], values: Array[T])(f: Boolean => Unit)

}