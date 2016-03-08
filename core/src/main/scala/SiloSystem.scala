package fp

import java.util.concurrent.atomic.AtomicInteger

import com.typesafe.scalalogging.{ StrictLogging => Logging }
import fp.model.{ ClientRequest, Populate, Populated, Response }

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.pickling._

/** Provides a set of operations needed to create [[SiloSystem]]s. */
object SiloSystem extends Logging {

  /** Instantiate a silo system.
    *
    * If `port` is `None`, the silo system runs in server mode.
    * Otherwise, the silo system runs in client mode.
    *
    * In Server mode, the silo system is extended by an underlying server
    * located at `localhost` listening at `port`. The underlying server
    * is required to host silos and make them available to other silo systems.
    *
    *
    * The actual silo system implementation must be a subclass of
    * [[fp.backend.SiloSystem]] with a default, empty constructor. The concrete
    * realization is specified by the system property `-Dfp.backend=<class>`.
    * If no system property is given, the realization defaults to
    * [[fp.backend.netty.SiloSystem]].
    *
    * In both server and client mode, Netty is used to realize the network layer.
    *
    * @param port Network port
    */
  def apply(port: Option[Int] = None): Future[SiloSystem] = Future {
    val clazz = sys.props.getOrElse("fp.backend", "fp.backend.netty.SiloSystem")
    logger.info(s"Initializing silo system with `$clazz`")
    Class.forName(clazz).newInstance().asInstanceOf[fp.backend.SiloSystem]
  } flatMap { system =>
    port match {
      case None => Future.successful(system)
      case Some(portNumber) => system withServer Host("127.0.0.1", portNumber)
    }
  }
}

/** Logical entry point to a collection of [[Silo]]s -- a Silo manager. */
trait SiloSystem extends SiloRefFactory with Logging {

  /** Name identifying a given silo system.
    *
    * In server mode, [[name]] defaults to `Host:Port`.
    * Otherwise, [[name]] defaults to a random [[java.util.UUID]].
    */
  def name: String

  /** Terminate the silo system. */
  def terminate(): Future[Unit]

  def request[R <: ClientRequest: Pickler](at: Host)(request: MsgId => R): Future[Response]

  /**
   * Return an realization agnostic silo system running in server mode.
   *
   * A silo system running in server mode has an underlying [[fp.backend.Server]]
   * to host silos and make those available to other silo systems.
   *
   * The underlying server is private to the silo system, i.e., only the silo
   * system itself directly communicates with the server. A user/client only
   * directly communicates with a silo system as such.
   *
   * @param at Target host
   */
  def withServer(at: Host): Future[fp.SiloSystem]

  object MsgIdGen {
    private lazy val ids = new AtomicInteger(10)
    def next = MsgId(ids.incrementAndGet())
  }

}


