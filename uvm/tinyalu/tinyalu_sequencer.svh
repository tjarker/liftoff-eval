class tinyalu_sequencer extends uvm_sequencer #(tinyalu_tx);
  `uvm_component_utils(tinyalu_sequencer)

  function new(string name, uvm_component parent);
    super.new(name, parent);
  endfunction

endclass