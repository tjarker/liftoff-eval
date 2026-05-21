// See README.md for license details.

import chisel3._
import chisel3.experimental.BundleLiterals._
import chisel3.simulator.scalatest.ChiselSim
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import scala.collection.mutable.Queue
import scala.util.Random
import chisel3.simulator.Settings
import svsim.BackendSettingsModifications
import svsim.Backend
import svsim.verilator.Backend.CompilationSettings.TraceStyle
import svsim.verilator.Backend.CompilationSettings.TraceKind

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


class TinyAluTest extends AnyFreeSpec with Matchers with ChiselSim {

  class TinyAluDriver(dut: TinyAluChisel, txs: Queue[TinyAlu.AluRequest]) {

    def step() = {
      if (!dut.io.start.peekBoolean() && !dut.io.done.peekBoolean()) {
        if (txs.nonEmpty) {
          val tx = txs.dequeue()
          dut.io.start.poke(1.B)
          dut.io.a.poke(tx.a.U)
          dut.io.b.poke(tx.b.U)
          dut.io.op.poke(tx.op)
        }
      } else if(dut.io.start.peekBoolean() && dut.io.done.peekBoolean()) {
        dut.io.start.poke(0.B)
      }
    }

  }

  class TinyAluMonitor(dut: TinyAluChisel, observedTxs: Queue[TinyAlu.AluResult]) {

    var currentTx: Option[TinyAlu.AluRequest] = None

    var waitForResult = false

    def step() = {

      if (waitForResult) {
        if (dut.io.done.peekBoolean()) {
          val result = dut.io.result.peek().litValue
          observedTxs.enqueue(TinyAlu.AluResult(currentTx.get, result))
          waitForResult = false
        }
      } else if (dut.io.start.peekBoolean()) {
        val a = dut.io.a.peek().litValue
        val b = dut.io.b.peek().litValue
        val op = dut.io.op.peek().litValue.toInt
        val req = TinyAlu.AluRequest(TinyAlu.Op.fromInt(op), a, b)
        currentTx = Some(req)
        waitForResult = true
      }
    }
  }

  "TinyAlu should calculate proper results" in {

    simulate(new TinyAluChisel) { dut =>

      val startNanos = System.nanoTime()


      dut.reset.poke(1.B)
      dut.clock.step()
      dut.reset.poke(0.B)

      val txs = Queue(Stimulus.benchmark(100000): _*)
      val observedTxs = Queue.empty[TinyAlu.AluResult]

      val driver = new TinyAluDriver(dut, txs)
      val monitor = new TinyAluMonitor(dut, observedTxs)

      while (txs.nonEmpty || monitor.waitForResult) {
        driver.step()
        monitor.step()
        dut.clock.step()
      }

      val endNanos = System.nanoTime()
      val durationSeconds = (endNanos - startNanos) / 1e9d
      println(f"Test completed in $durationSeconds%.3f seconds")

      println(s"Observed transactions: ${observedTxs.length}")
  
    }
  }
}

