import collection.mutable

import chisel3._
import liftoff._
import scala.util.Random

import TinyAlu._
import chisel3.util.HasBlackBoxPath

class TinyAluBB extends BlackBox with HasBlackBoxPath {
  override val desiredName = "tinyalu"
  val io = IO(new Bundle {
    val clk = Input(Clock())
    val reset_n = Input(Bool())
    val start = Input(Bool())
    val A = Input(UInt(8.W))
    val B = Input(UInt(8.W))
    val op = Input(UInt(3.W))
    val done = Output(Bool())
    val result = Output(UInt(16.W))
  })
  addPath("../../src/verilog/tinyalu.sv")
}

class TinyAlu extends Module {

  val io = IO(new Bundle {
    val start = Input(Bool())
    val a = Input(UInt(8.W))
    val b = Input(UInt(8.W))
    val op = Input(TinyAlu.Op())
    val done = Output(Bool())
    val result = Output(UInt(16.W))
  })

  val bb = Module(new TinyAluBB)
  bb.io.clk := clock
  bb.io.reset_n := !reset.asBool
  bb.io.start := io.start
  bb.io.A := io.a
  bb.io.B := io.b
  bb.io.op := io.op.asUInt
  io.done := bb.io.done
  io.result := bb.io.result

}

object TinyAlu {

  object Op extends ChiselEnum {
    val Add = Value(1.U)
    val And = Value(2.U)
    val Xor = Value(3.U)
    val Mul = Value(4.U)

    def fromInt(i: Int): Op.Type = i match {
      case 1 => Add
      case 2 => And
      case 3 => Xor
      case 4 => Mul
    }
    def toString(op: Op.Type): String = op match {
      case Add => "+"
      case And => "&"
      case Xor => "^"
      case Mul => "*"
    }
    def random(): Op.Type = all(Random.nextInt(all.length))
  }


  object AluRequest {
    def random(): AluRequest = {
      val a = Random.nextInt(256)
      val b = Random.nextInt(256)
      val op = Op.random()
      AluRequest(op, a, b)
    }
  }
  case class AluRequest(val op: Op.Type, val a: BigInt, val b: BigInt) {
    def randomize(): AluRequest = {
      val a = Random.nextInt(256)
      val b = Random.nextInt(256)
      val op = Op.random()
      AluRequest(op, a, b)
    }

    override def toString(): String = s"AluRequest($a ${Op.toString(op)} $b)"
  }

  case class AluResult(val req: AluRequest, val result: BigInt) {
    override def toString(): String = s"AluResult($req = $result)"
  }

  def prediction(t: AluRequest): AluResult = {
    val a = t.a
    val b = t.b
    val op = t.op
    AluResult(
      t,
      op match {
        case Op.Add => a + b
        case Op.And => a & b
        case Op.Xor => a ^ b
        case Op.Mul => a * b
      }
    )
  }

}

class TinyAluBfm(dut: TinyAlu) {

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
    dut.clock.stepUntil(dut.io.done, 1.B)
  }

  def waitForStart() = {
    dut.clock.stepUntil(dut.io.start, 1.B)
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
  object DUT extends Config[TinyAlu]

  case class AluTestConfig(
    val CoverageErrors: Boolean = true,
    val CheckErrors: Boolean = true
  )

  object AluTestConfigNoErr extends AluTestConfig(
    CoverageErrors = false,
    CheckErrors = false
  )

  object AluTestConfig extends Config[AluTestConfig](AluTestConfigNoErr)

  object BenchmarkReps extends Config[Int]

}

class AluDriver
    extends Driver[AluRequest, AluResult] with
      SimPhase {

  val dut = Config.get(TinyAluTb.DUT)
  val bfm = new TinyAluBfm(dut)
  def sim() = forever {
    val t = next()
    val res = bfm.driveRequest(t)
    done(res)
    dut.clock.step()
  }

}

class AluMonitor extends Monitor[AluResult] with SimPhase {

  val dut = Config.get(TinyAluTb.DUT)
  val bfm = new TinyAluBfm(dut)

  def sim() = Task.withRegion(Region.Monitor) { forever {
      val tx = bfm.observeTransaction()
      publish(tx)
      dut.clock.step()
    }
  }

  
}

class AluScoreboard
    extends AnalysisComponent[AluResult] with
      ReportPhase {

  val passing = mutable.ListBuffer[AluResult]()
  val failing = mutable.ListBuffer[AluResult]()

  def sim() = foreachTx { tx =>
    val pred = TinyAlu.prediction(tx.req)
    if (tx.result == pred.result) {
      passing += tx
    } else {
      failing += tx
    }
  }

  def report() = {
    Reporting.info(None, s"Total transactions: ${passing.size + failing.size}")
    passing.foreach { tx =>
      //Reporting.info(None, s"PASSED: $tx")
    }
    failing.foreach { tx =>
      if (Config.get(TinyAluTb.AluTestConfig).CheckErrors) {
        Reporting.error(None, s"FAILED: $tx expected ${TinyAlu.prediction(tx.req)}")
      } else {
        Reporting.info(None, s"FAILED: $tx expected ${TinyAlu.prediction(tx.req)}")
      }
    }
  }

}


class AluCoverage extends AnalysisComponent[AluResult] {

  val ops = mutable.Map[TinyAlu.Op.Type, Int](TinyAlu.Op.all.map(_ -> 0): _*)

  def sim() = foreachTx { tx =>
    val hits = ops(tx.req.op)
    ops(tx.req.op) = hits + 1
  }

  def report() = {
    if (Config.get(TinyAluTb.AluTestConfig).CoverageErrors) {
      ops.foreach { case (op, cnt) =>
        if (cnt == 0) Reporting.error(None, s"Operation $op not covered")
      }
      if (!ops.exists(_._2 == 0)) Reporting.info(None, "All operations covered")
    }
  }

}

class AluEnv extends Component {

  val driver = Component.create[AluDriver]()
  val monitor = Component.create[AluMonitor]()
  val scoreboard = Component.create[AluScoreboard]()
  val coverage = Component.create[AluCoverage]()

  monitor.addSubscriber(scoreboard)
  monitor.addSubscriber(coverage)
}

object Stimulus {

  def random() = BiGen[AluResult, AluRequest] {
    for (op <- TinyAlu.Op.all) {
      val a = Random.nextInt(256)
      val b = Random.nextInt(256)
      Gen.emit(AluRequest(op, a, b))
    }
  }

  def max() = BiGen[AluResult, AluRequest] {
    for (op <- TinyAlu.Op.all) {
      Gen.emit(AluRequest(op, 0xFF, 0xFF))
    }
  }

  def apply(op: TinyAlu.Op.Type, a: BigInt, b: BigInt) = BiGen[AluResult, AluRequest] {
    Gen.emit(AluRequest(op, a, b))
  }

  def fibonacci() = BiGen[AluResult, AluRequest] {
    var a = 0
    var b = 1
    for (i <- 0 until 7) {
      val res: AluResult = Gen.emit(AluRequest(TinyAlu.Op.Add, a, b)).get
      val c = res.result.toInt
      a = b
      b = c
    }
  }

  def all() = Gen.concat(
    random(),
    max(),
    fibonacci()
  )

  def allParallel() = Gen.shuffle(
    random(),
    max(),
    fibonacci()
  )

  def benchmark() = Gen.repeat(Config.get(TinyAluTb.BenchmarkReps))(all())

}



class AluTest(dut: TinyAlu) extends Test with ResetPhase {

  Config.set(TinyAluTb.DUT, dut)
  

  val bfm = new TinyAluBfm(dut)
  val env = Component.builder
    .withParam(TinyAluTb.DUT, dut)
    .withParam(TinyAluTb.AluTestConfig, TinyAluTb.AluTestConfigNoErr)
    .withTypeOverride[AluDriver, AluDriver]
    .create[AluEnv]()

  def reset() = {
    bfm.reset()
  }

  def sequence(): BiGen[AluResult, AluRequest] = Stimulus.random()

  def test() = {
    Config.set(TinyAluTb.BenchmarkReps, 100000) // TODO: the test does not inherit the components context
    Random.setSeed(42)
    val seq = sequence()
    env.driver.drive(seq).awaitDone()
    dut.clock.step()
  }
}

class FibonacciTest(dut: TinyAlu) extends AluTest(dut) {

  override def sequence() = Stimulus.fibonacci()

}

class ParallelTest(dut: TinyAlu) extends AluTest(dut) {

  override def sequence() = Stimulus.allParallel()

}

class BenchmarkTest(dut: TinyAlu) extends AluTest(dut) {

  

  override def sequence() = Stimulus.benchmark()

}

object AluTests extends App {

  val buildDir = "build/alu_tests".toDir
  
  val model = ChiselModel(new TinyAlu, buildDir)

  Reporting.addProviderFilter("SimController.Loop")
  Reporting.addProviderFilter("SimController.Queue")

  // model.simulate(buildDir.addSubDir(buildDir / "random")) { dut =>
  //   dut.io.done.dependsCombinationallyOn(Seq(dut.io.op))
  //   Test.run(new AluTest(dut))
  // }

  // model.simulate(buildDir.addSubDir(buildDir / "fibonacci")) { dut =>
  //   Test.run(new FibonacciTest(dut))
  // }

  // model.simulate(buildDir.addSubDir(buildDir / "parallel")) { dut =>
  //   Test.run(new ParallelTest(dut))
  // }

  model.simulate(buildDir.addSubDir(buildDir / "benchmark")) { dut =>
    Test.run(new BenchmarkTest(dut))
  }

}

// @main def AluTestRandom(): Unit =
//   Test.run(new TinyAlu, 1.ps, Some("alu_rand.vcd"))(new AluTest(_))

// @main def AluTestFibonacci(): Unit =
//   Test.run(new TinyAlu, 1.ps, Some("alu_fib.vcd"))(new FibonacciTest(_))


// @main def AluTestParallel(): Unit =
//   Test.run(new TinyAlu, 1.ps, Some("alu_parallel.vcd"))(new ParallelTest(_))
