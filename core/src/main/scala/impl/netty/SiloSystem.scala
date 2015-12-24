package silt
package impl
package netty

import java.util.concurrent.{ BlockingQueue, CountDownLatch, LinkedBlockingQueue }

import scala.concurrent.{ Await, ExecutionContext, Future, Promise }
import ExecutionContext.Implicits.{ global => executor }

import com.typesafe.scalalogging.{ StrictLogging => Logging }

/** A Netty-based implementation of a silo system. */
private[netty] class SiloSystem() extends AnyRef with impl.SiloSystem with Logging {

  self: silt.SiloSystem =>

  /* Synchronizer for this silo system.
   *
   * A silo system running in server mode keeps on running until the latch
   * reaches the terminal state. That is, the gate is closed and no thread can
   * pass. In the terminal state the gate opens, allowing all threads to pass,
   * say, to terminate the silo system and the server, respectively.
   */
  val hook: Option[CountDownLatch] = None

  override val name = (new java.rmi.dgc.VMID()).toString

  override def withServer(at: Option[Host]): Future[silt.SiloSystem] = at match {
    case None => Future.successful(this) // XXX run in client mode
    case Some(host) =>
      /* Promise a silo system, and fulfill this promise with the completed
       * startup of the underlying server. Cf. [[Server#run]].
       */
      val started = Promise[silt.SiloSystem]
      executor execute new SiloSystem() with Server {

        self: Server =>

        // silt.SiloSytem w/ internals
        override val name = host.toString
        override val server = Some(self)

        // silt.Server
        override val at = host

        // silt.impl.netty.SiloSystem
        override val hook = Some(new CountDownLatch(1))
        override val up = started

        (new Thread { override def run(): Unit = hook map (_.await()) }).start()

      }
      started.future
  }

  override def terminate(): Unit =
    //XXX statusOf map {
    //  case (_, Connected(ch, workerGroup)) =>
    //    // XXX Terminate Netty Channel: sendToChannel(ch, Terminate())
    //    // XXX sync vs. await: ch.closeFuture().sync()
    //    workerGroup.shutdownGracefully()
    //  case _ => /* do nothing */
    //}
    server map { server =>
      logger.debug("Silo system stops underlying server...")
      /* XXX re-assess sending specific, internal termination message to server
       * to be enqueued at the internal message queue (`mq`)
       */
      server.stop()
      hook map (_.countDown())
    }

}

// vim: set tw=80 ft=scala: