class tinyalu_driver extends uvm_driver #(tinyalu_tx);
  virtual tinyalu_if vif;

  `uvm_component_utils(tinyalu_driver)

  function new(string name, uvm_component parent);
    super.new(name, parent);
  endfunction : new

  function void build_phase(uvm_phase phase);
    super.build_phase(phase);
    if (!uvm_config_db#(virtual tinyalu_if)::get(this, "", "vif", vif))
      `uvm_fatal("NO_VIF", {"virtual interface must be set for: ", get_full_name(), ".vif"});
  endfunction : build_phase

  virtual task run_phase(uvm_phase phase);
    forever begin
      seq_item_port.get_next_item(req);
      //phase.raise_objection(this);
      drive();
      seq_item_port.item_done();
      //phase.drop_objection(this);
    end
  endtask : run_phase

  virtual task drive();
    vif.start = 1;
    vif.A = req.A;
    vif.B = req.B;
    vif.op = req.op;
    // Wait for the DUT to assert 'done'
    @(posedge vif.clk);
    wait (vif.done == 1);
    req.result = vif.result;
    @(negedge vif.clk);
    vif.start = 0;
  endtask : drive

endclass : tinyalu_driver
