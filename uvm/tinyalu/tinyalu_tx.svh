
class tinyalu_tx extends uvm_sequence_item;
  `uvm_object_utils(tinyalu_tx)
  rand bit [7:0] A;
  rand bit [7:0] B;
  rand bit [2:0] op;
  bit [15:0] result;

  function new(string name = "tinyalu_tx");
    super.new(name);
  endfunction : new

  constraint c_op {
    // only 1, 2, 3 and 4 are valid operations
    op inside {3'b001, 3'b010, 3'b011, 3'b100};
  }

endclass : tinyalu_tx