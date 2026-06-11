class tinyalu_monitor extends uvm_monitor;
  virtual tinyalu_if vif;
  uvm_analysis_port #(tinyalu_tx) item_collected_port;
  tinyalu_tx trans_collected;

  bit wait_for_result;

  `uvm_component_utils(tinyalu_monitor)

  function new(string name, uvm_component parent);
    super.new(name, parent);
    trans_collected = null;
    wait_for_result = 0;
    item_collected_port = new("item_collected_port", this);
  endfunction : new

  function void build_phase(uvm_phase phase);
    super.build_phase(phase);
    if (!uvm_config_db#(virtual tinyalu_if)::get(this, "", "vif", vif))
      `uvm_fatal("NO_VIF", {"virtual interface must be set for: ", get_full_name(), ".vif"});
  endfunction : build_phase

  virtual task run_phase(uvm_phase phase);
    forever begin
      @(posedge vif.clk);
      //`uvm_info("tinyalu_monitor", "Clock edge detected", UVM_LOW);
      if (vif.start == 1 && !wait_for_result) begin
        //`uvm_info("tinyalu_monitor", "Start detected", UVM_LOW);
        wait_for_result = 1;
        trans_collected = tinyalu_tx::type_id::create("trans_collected");
        trans_collected.A = vif.A;
        trans_collected.B = vif.B;
        trans_collected.op = vif.op;
        //`uvm_info("tinyalu_monitor", $sformatf("Collected transaction start: A=%0h, B=%0h, op=%0d", trans_collected.A, trans_collected.B, trans_collected.op), UVM_LOW);
      end
      #1;
      if (vif.done == 1 && wait_for_result) begin
        //`uvm_info("tinyalu_monitor", "Result ready", UVM_LOW);
        wait_for_result = 0;
        trans_collected.result = vif.result;
        //`uvm_info("tinyalu_monitor", $sformatf("Collected transaction: A=%0h, B=%0h, op=%0d, result=%0h", trans_collected.A, trans_collected.B, trans_collected.op, trans_collected.result), UVM_LOW);
        item_collected_port.write(trans_collected);
        trans_collected = null;
      end
      
    end

  endtask : run_phase

endclass : tinyalu_monitor
