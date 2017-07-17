package angela.utils

import java.io.{DataOutputStream, File}

import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.fs.FSDataOutputStream

/**
  * Created by tuoyu on 11/07/2017.
  */
object FileUtils {

  def initLocalFile(path: String)(f: java.io.File => Unit): Unit = {
    val file = new File(path)
    f(file)
  }


  /**
    * Init HDFS File Handler for Writing
    *
    * @param path HDFS File Path
    * @param f    file Handler
    */
  def initHDFSFile(path: String, conf: Configuration)(f: FSDataOutputStream => Unit): Unit = {
    import org.apache.hadoop.fs.{FileSystem, Path}

    val fs = FileSystem.get(conf)
    val output = fs.create(new Path(path))
    f(output)
  }


  /**
    * 写入文件本地文件的方法
    *
    * @param f
    * @param op
    */
  def printToFile(
                   f: java.io.File
                 )(
                   op: java.io.PrintWriter => Unit
                 ) = {
    val p = new java.io.PrintWriter(f)
    try {
      op(p)
    } finally {
      p.close()
    }
  }

  def printToFile(
                   f: DataOutputStream
                 )(
                   op: java.io.PrintWriter => Unit
                 ) = {
    val p = new java.io.PrintWriter(f)
    try {
      op(p)
    } finally {
      p.close()
    }
  }
}
