class tinyalu_env extends uvm_env;
 
  tinyalu_agent agent;
  tinyalu_scoreboard scoreboard;
  tinyalu_coverage coverage;

  `uvm_component_utils(tinyalu_env)

  function new(string name, uvm_component parent);
    super.new(name, parent);
  endfunction : new

  function void build_phase(uvm_phase phase);
    super.build_phase(phase);
    agent = tinyalu_agent::type_id::create("agent", this);
    scoreboard = tinyalu_scoreboard::type_id::create("scoreboard", this);
    coverage = tinyalu_coverage::type_id::create("coverage", this);
  endfunction : build_phase

  function void connect_phase(uvm_phase phase);
    agent.monitor.item_collected_port.connect(scoreboard.item_collected.analysis_export);
    agent.monitor.item_collected_port.connect(coverage.analysis_export);
  endfunction : connect_phase

endclass : tinyalu_env
