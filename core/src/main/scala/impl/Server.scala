package silt
package impl

import java.util.concurrent.{ CountDownLatch, LinkedBlockingQueue }

import scala.concurrent.{ ExecutionContext, Promise }
import ExecutionContext.Implicits.{ global => executor }

import io.netty.bootstrap.ServerBootstrap
import io.netty.channel.ChannelInitializer
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.handler.logging.{ LogLevel, LoggingHandler => Logger }

import com.typesafe.scalalogging.{ StrictLogging => Logging }

trait Server extends AnyRef with silt.Server with Runnable with Logging {

  self: silt.SiloSystem =>

  import logger._

  /** Promise the server is up and running.
    *
    * To fulfill this promise use `self` tied to [[silt.SiloSystem]].
    */
  protected def started: Promise[silt.SiloSystem with Server]

  // Netty server constituents
  private val server = new ServerBootstrap
  private val boss = new NioEventLoopGroup
  private val worker = new NioEventLoopGroup

  // XXX new LengthFieldBasedFrameDecoder ?
  // XXX new ChunkedWriteHandler() ?
  private val encoder = new SystemMessageEncoder()
  private val decoder = new SystemMessageDecoder()
  // XXX private val forwarder = new Forwarder(processor)

  private val latch: CountDownLatch = new CountDownLatch(1)

  // Worker for all incoming messages from all channels.
  //private val receptor = new Receptor(self, new LinkedBlockingQueue[Incoming]())

  /* Initialize a [[Netty-based http://goo.gl/0Z9pZM]] server. */
  trace("Server initializing...")
  server.group(boss, worker)
    .channel(classOf[NioServerSocketChannel])
    .childHandler(new ChannelInitializer[SocketChannel]() {
      override def initChannel(ch: SocketChannel): Unit = {
        val pipeline = ch.pipeline()
        pipeline.addLast("Logger", new Logger(LogLevel.TRACE))
        pipeline.addLast("Encoder", encoder)
        pipeline.addLast("Decoder", decoder)
        // XXX pipeline.addLast("Server|Forwarder", forwarder)
      }
    })
  // XXX are those options necessary?
  //.option(ChannelOption.SO_BACKLOG.asInstanceOf[ChannelOption[Any]], 128) 
  //.childOption(ChannelOption.SO_KEEPALIVE.asInstanceOf[ChannelOption[Any]], true)
  trace("Server initializing done.")

  // Members declared in silt.Server

  /** Start server.
    *
    * Start and bind server to accept incoming connections at port `at.port`.
    */
  override def start(): Unit = {
    trace("Server start...")

    // XXX receptor.start()
    server.bind(at.port).sync()
    started success self

    trace("Server start done.")
    info(s"Server listining at port ${at.port}.")

    (new Thread { override def run(): Unit = latch.await() }).start()
  }

  /** Stop server.
    *
    * In Nety 4.0, you can just call `shutdownGracefully` on the
    * `EventLoopGroup` that manages all your channels. Then all ''existing
    * channels will be closed automatically'' and reconnection attempts should
    * be rejected.
    */
  override def stop(): Unit = {
    trace("Server stop...")

    // XXX receptor.stop()
    worker.shutdownGracefully()
    boss.shutdownGracefully()
    latch.countDown()

    trace("Server stop done.")
  }

  // Members declared in java.lang.Runnable

  override def run(): Unit = start()
}

// vim: set tw=80 ft=scala:
