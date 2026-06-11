import collection.mutable

import chisel3._
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

class TinyAluChisel extends Module {

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


// @main def AluTestRandom(): Unit =
//   Test.run(new TinyAlu, 1.ps, Some("alu_rand.vcd"))(new AluTest(_))

// @main def AluTestFibonacci(): Unit =
//   Test.run(new TinyAlu, 1.ps, Some("alu_fib.vcd"))(new FibonacciTest(_))


// @main def AluTestParallel(): Unit =
//   Test.run(new TinyAlu, 1.ps, Some("alu_parallel.vcd"))(new ParallelTest(_))

