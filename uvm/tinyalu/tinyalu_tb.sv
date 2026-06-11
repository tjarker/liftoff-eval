module tinyalu_tb;
  import uvm_pkg::*;
  import tinyalu_pkg::*;

  logic clk;
  logic reset_n;

  always #5 clk = ~clk;

  initial begin
    reset_n = 0;
    #12 reset_n = 1;
  end

  tinyalu_if intf (
    clk, reset_n
  );

  tinyalu dut (
    .A(intf.A),
    .B(intf.B),
    .op(intf.op),
    .clk(clk),
    .reset_n(reset_n),
    .start(intf.start),
    .done(intf.done),
    .result(intf.result)
  );

  initial begin
    uvm_config_db#(virtual tinyalu_if)::set(uvm_root::get(), "*", "vif", intf);
  end

  initial begin
    run_test();
  end

  //dump waveforms
  initial begin
    $dumpfile("tinyalu_tb.fst");
    $dumpvars(0, tinyalu_tb);
  end

endmodule
