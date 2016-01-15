package multijvm
package cs 

import scala.concurrent.{ Await, ExecutionContext, Future, Promise }
import ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.util.{ Success, Failure }

import com.typesafe.scalalogging.{ StrictLogging => Logging }

import silt._

/** A silo system running in server mode can be understood as a ''slave'',
 *  ''worker'', ''workhorse'', or ''executor''. You name it! 
  * 
  * All these terms have their raison d'être, i.e., all these terms, in general,
  * state the fact that this node is for hosting silos and thus for executing
  * computations defined and shipped by some other node --- here, in this
  * example, that other node is `Node2`.
  * 
  * To allow for creation of silos by other nodes, the F-P runtime requires a
  * web server. Current default is a netty-based web server. 
  */
object ExampleMultiJvmServer extends AnyRef with Logging {

  import logger._

  def main(args: Array[String]): Unit = 
    /* Start a silo system in server mode.
     */
    SiloSystem(port = Some(8090)) match {
      case Success(sys) => sys onComplete {
        case Success(sys) => info(s"Silo system in server mode up and running (${sys.name}).")
        case Failure(err)  => error(s"Could not start silo system in server mode:\n ${err.getMessage}")
      }
      case Failure(err) => error(s"Could not instantiate silo system at `Node1`:\n ${err.getMessage}")
    }

}

// vim: set tw=80 ft=scala: