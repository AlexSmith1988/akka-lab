package streaming.graphs

import java.io.File

import akka.actor.{Actor, ActorSystem, Props}
import akka.dispatch.forkjoin.ThreadLocalRandom
import akka.stream.scaladsl.{Broadcast, FileIO, Flow, GraphDSL, Keep, RunnableGraph, Sink, Source}
import akka.stream.{ActorMaterializer, ClosedShape}
import akka.util.ByteString
import streaming.graphs.Consumer2._

import scala.util.control.NonFatal
import scala.util.{Failure, Success}
/*
created by Ilya Volynin at 18.12.17
*/
object FileIOGraphFilterActor1 extends App {
  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()
  import system.dispatcher
  val consumer = system.actorOf(Props[Consumer2], "total2")
  val sinkConsumer = Sink.actorRefWithAck(consumer, Init, Ack, Complete(100), errorHandler)
  val source = Source.fromIterator { () => Iterator.continually(ThreadLocalRandom.current().nextInt(500000)) }
  val fileSink = FileIO.toFile(new File("random.txt"))
  val slowSink = Flow[Int].map(i => {
    Thread.sleep(100)
    ByteString(i.toString + s" ${Thread.currentThread().getId}" + "\n")
  }).toMat(fileSink)(Keep.right)
  val odd = Flow[Int].filter(_ % 2 == 1)
  val even = Flow[Int].filter(_ % 2 == 0)
  val toCons = Flow[Int].map(i => {
    Process(i, 0)
  })
  val consoleSink = Sink.foreach[Int](i => println(s"$i: ${Thread.currentThread().getId}"))
  val graph = GraphDSL.create(slowSink, consoleSink)((slow, _) => slow) { implicit builder =>
    (slow, console) =>
      import GraphDSL.Implicits._
      val broadcast = builder.add(Broadcast[Int](3))
      source ~> broadcast ~> even ~> slow
      broadcast ~> odd ~> console
      broadcast ~> even ~> toCons ~> sinkConsumer
      ClosedShape
  }
  val materialized = RunnableGraph.fromGraph(graph).run()
  materialized.onComplete {
    case Success(_) =>
      system.terminate()
    case Failure(e) =>
      println(s"Failure: ${e.getMessage}")
      system.terminate()
  }
}
object Consumer2 {
  case object Init
  case object Ack
  case class Complete(id: Long)
  case class Process(value: Long, lastMessage: Long)
  def errorHandler(ex: Throwable): Unit = {
    ex match {
      case NonFatal(e) => println("exception happened: " + e)
    }
  }
}
class Consumer2 extends Actor {
  override def receive: Receive = {
    case _: Init.type =>
      println(s"init")
      sender ! Ack
    case Process(value, _) =>
      println(s"v=$value")
      if (value > 450000) sender ! errorHandler(new IllegalStateException("too large value"))
        sender ! Ack
    case Complete(id) =>
      println(s"completed $id")
      sender ! Ack
  }
}
