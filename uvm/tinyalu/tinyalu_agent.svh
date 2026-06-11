class tinyalu_agent extends uvm_agent;
  tinyalu_driver    driver;
  tinyalu_sequencer sequencer;
  tinyalu_monitor   monitor;

  `uvm_component_utils(tinyalu_agent)

  function new(string name, uvm_component parent);
    super.new(name, parent);
  endfunction : new

  function void build_phase(uvm_phase phase);
    super.build_phase(phase);
    if (get_is_active() == UVM_ACTIVE) begin
      driver = tinyalu_driver::type_id::create("driver", this);
      sequencer = tinyalu_sequencer::type_id::create("sequencer", this);
    end
    monitor = tinyalu_monitor::type_id::create("monitor", this);
  endfunction : build_phase

  function void connect_phase(uvm_phase phase);
    if (get_is_active() == UVM_ACTIVE) begin
      driver.seq_item_port.connect(sequencer.seq_item_export);
    end
  endfunction : connect_phase

endclass : tinyalu_agent
