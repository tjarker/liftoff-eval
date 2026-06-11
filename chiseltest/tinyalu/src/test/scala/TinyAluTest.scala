
import chisel3._
import chiseltest._

import TinyAlu._
import org.scalatest.flatspec.AnyFlatSpec

import scala.collection.mutable
import scala.util.Random

class TinyAluTest extends AnyFlatSpec with ChiselScalatestTester {

  "TinyAlu" should "run Random" in {
    test(new TinyAluChisel).withAnnotations(Seq(WriteFstAnnotation, VerilatorBackendAnnotation)) { dut =>
      val startNanos = System.nanoTime()
      var cycles = 0
      var stopCounting = false
      fork {
        while(!stopCounting) {
          dut.clock.step()
          cycles += 1
        }
      }
      val test = new AluTest(dut, Stimulus.benchmark(100000))
      test.test()
      test.kill()
      test.report()
      stopCounting = true
      val stopNanos = System.nanoTime()
      
      val runtime = (stopNanos - startNanos) / 1e9d
      val hertz = cycles / runtime
      println(s"Runtime = ${(stopNanos - startNanos) / 1e9d}")
      println(s"Hertz = $hertz")
    }
  }

}



class TinyAluBfm(dut: TinyAluChisel) {

  def reset() = {
    dut.reset.poke(1.B)
    dut.io.start.poke(0.B)
    dut.clock.step()
    dut.reset.poke(0.B)
    dut.clock.step()
  }

  def driveRequest(t: AluRequest): AluResult = {
    import t.{a, b, op}
    dut.io.a.poke(a)
    dut.io.b.poke(b)
    dut.io.op.poke(op)
    dut.io.start.poke(1.B)
    dut.clock.step()
    waitForDone()
    dut.io.start.poke(0.B)
    AluResult(t, getResult())
  }

  def waitForDone() = {
    while(!dut.io.done.peekBoolean) dut.clock.step()
  }

  def waitForStart() = {
    while(!dut.io.start.peekBoolean) dut.clock.step()
  }

  def getResult() = {
    dut.io.result.peek().litValue
  }

  def observeTransaction() = {
    waitForStart()
    val t = AluRequest(
      TinyAlu.Op.fromInt(dut.io.op.peek().litValue.toInt),
      dut.io.a.peek().litValue,
      dut.io.b.peek().litValue
    )
    dut.clock.step()
    waitForDone()
    val res = dut.io.result.peek().litValue
    AluResult(t, res)
  }

}

object TinyAluTb {

  case class AluTestConfig(
    val CoverageErrors: Boolean = true,
    val CheckErrors: Boolean = true
  )

  object AluTestConfigNoErr extends AluTestConfig(
    CoverageErrors = false,
    CheckErrors = false
  )

}

class AluDriver(dut: TinyAluChisel) {

  var shouldRun = true
  def kill() = shouldRun = false

  val queue = mutable.Queue[AluRequest]()

  val bfm = new TinyAluBfm(dut)
  def run() = fork {
    while (shouldRun) {
      while (queue.isEmpty) dut.clock.step()
      val t = queue.dequeue()
      val res = bfm.driveRequest(t)
      dut.clock.step()
    }
  }

  def enqueue(tx: AluRequest) = queue += tx

}

class AluMonitor(dut: TinyAluChisel, subscribers: Seq[mutable.Queue[AluResult]]) {

  var shouldRun = true
  def kill() = shouldRun = false

  val bfm = new TinyAluBfm(dut)

  def run() = fork.withRegion(Monitor) {
    while (shouldRun) {
      val res = bfm.observeTransaction()
      subscribers.foreach(_ += res)
      dut.clock.step()
    }
  }

  
}

class AluScoreboard(observedTxs: mutable.Queue[AluResult], step: () => Unit, checkErrors: Boolean) {

  var shouldRun = true
  def kill() = shouldRun = false

  val passing = mutable.ListBuffer[AluResult]()
  val failing = mutable.ListBuffer[AluResult]()

  def run() = fork.withRegion(Monitor) {
    while(shouldRun) {
      while(observedTxs.isEmpty) step()
      val tx = observedTxs.dequeue()
      val pred = TinyAlu.prediction(tx.req)
      if (tx.result == pred.result) {
        passing += tx
      } else {
        failing += tx
      }
    }
    
  }

  def report() = {
    println(s"Total transactions: ${passing.size + failing.size}")
    passing.foreach { tx =>
      //Reporting.info(None, s"PASSED: $tx")
    }
    failing.foreach { tx =>
      if (checkErrors) {
        println(s"FAILED: $tx expected ${TinyAlu.prediction(tx.req)}")
      } else {
        println(None, s"FAILED: $tx expected ${TinyAlu.prediction(tx.req)}")
      }
    }
  }

}


class AluCoverage(observedTxs: mutable.Queue[AluResult], step: () => Unit, coverageErrors: Boolean) {

  var shouldRun = true
  def kill() = shouldRun = false

  val ops = mutable.Map[TinyAlu.Op.Type, Int](TinyAlu.Op.all.map(_ -> 0): _*)

  def run() = fork.withRegion(Monitor) {
    while(shouldRun) {
      while(observedTxs.isEmpty) step()
      val tx = observedTxs.dequeue()
      val hits = ops(tx.req.op)
      ops(tx.req.op) = hits + 1
    }
  }

  def report() = {
    if (coverageErrors) {
      ops.foreach { case (op, cnt) =>
        if (cnt == 0) println(s"Operation $op not covered")
      }
      if (!ops.exists(_._2 == 0)) println("All operations covered")
    }
  }

}

class AluEnv(dut: TinyAluChisel, checkErrors: Boolean, coverageErrors: Boolean) {

  val scoreboardQueue = mutable.Queue[AluResult]()
  val coverageQueue = mutable.Queue[AluResult]()

  val driver = new AluDriver(dut)
  val monitor = new AluMonitor(dut, Seq(scoreboardQueue, coverageQueue))
  val scoreboard = new AluScoreboard(scoreboardQueue, () => dut.clock.step(), checkErrors)
  val coverage = new AluCoverage(coverageQueue, () => dut.clock.step(), coverageErrors)


  def allTxsProcessed = scoreboardQueue.isEmpty && coverageQueue.isEmpty
}
object Stimulus {

  def random(): Seq[TinyAlu.AluRequest] = 
    for (op <- TinyAlu.Op.all) 
    yield TinyAlu.AluRequest(op, Random.nextInt(256), Random.nextInt(256))

  def max(): Seq[TinyAlu.AluRequest] = 
    for (op <- TinyAlu.Op.all) 
    yield TinyAlu.AluRequest(op, 255, 255)

  def fibonacci(n: Int): Seq[TinyAlu.AluRequest] = {
    val fibs = (0 until n).scanLeft((0, 1)) { case ((a, b), _) => (b, a + b) }.map(_._1)
    fibs.sliding(2).map { case Seq(a, b) => TinyAlu.AluRequest(TinyAlu.Op.Add, a, b) }.toSeq
  }

  def all: Seq[TinyAlu.AluRequest] = random() ++ max() ++ fibonacci(7)

  def benchmark(n: Int): Seq[TinyAlu.AluRequest] = Seq.fill(n)(all).flatten

}


class AluTest(dut: TinyAluChisel, stim: Seq[AluRequest]) {
  

  val bfm = new TinyAluBfm(dut)
  val env = new AluEnv(dut, true, true)

  env.driver.run()
  env.monitor.run()
  env.coverage.run()
  env.scoreboard.run()

  def reset() = {
    bfm.reset()
  }

  def test() = {

    Random.setSeed(42)

    stim.foreach(env.driver.enqueue)

    while(env.driver.queue.nonEmpty) dut.clock.step()

    while(!dut.io.done.peekBoolean) dut.clock.step()

    dut.clock.step()

    while(!env.allTxsProcessed) dut.clock.step()

  }

  def kill() = {
    env.driver.kill()
    env.monitor.kill()
    env.coverage.kill()
    env.scoreboard.kill()
  }

  def report() = {
    env.scoreboard.report()
    env.coverage.report()
  }
}
