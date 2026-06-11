class tinyalu_test extends uvm_test;
  `uvm_component_utils(tinyalu_test)

  tinyalu_env env;
  tinyalu_sequence seq;

  function new(string name = "tinyalu_test", uvm_component parent = null);
    super.new(name, parent);
  endfunction : new

  virtual function void build_phase(uvm_phase phase);

    // set rep parameter for benchmark
    uvm_config_db#(int)::set(uvm_root::get(), "*", "reps", 1000000);
    set_type_override_by_type(tinyalu_sequence::get_type(), bench_seq::get_type());

    super.build_phase(phase);
    env = tinyalu_env::type_id::create("env", this);
    seq = tinyalu_sequence::type_id::create("seq");
  endfunction : build_phase

  task run_phase(uvm_phase phase);
    phase.raise_objection(this);
    `uvm_info("run_phase", "Running the test", UVM_LOW);
    #10
    seq.start(env.agent.sequencer);
    `uvm_info("run_phase", "Test completed", UVM_LOW);
    phase.drop_objection(this);
  endtask : run_phase

endclass : tinyalu_test
