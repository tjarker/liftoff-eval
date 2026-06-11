class tinyalu_coverage extends uvm_subscriber #(tinyalu_tx);

  `uvm_component_utils(tinyalu_coverage)

  typedef enum {
    ADD,
    AND,
    XOR,
    MUL
  } ops_t;

  // Tracks which operations have been observed
  bit covered_ops[ops_t];

  function new(string name = "coverage", uvm_component parent = null);
    super.new(name, parent);
  endfunction

  virtual function void end_of_elaboration_phase(uvm_phase phase);
    super.end_of_elaboration_phase(phase);
    covered_ops.delete();
  endfunction

  virtual function void write(tinyalu_tx t);
    covered_ops[op_to_enum(t.op)] = 1;
  endfunction

  virtual function void report_phase(uvm_phase phase);
    bit disable_errors;
    ops_t op;
    string missed[$];

    disable_errors = 0;

    if (disable_errors)
      return;

    for (int i = 1; i <= 4; i++) begin
      op = op_to_enum(i);
      if (!covered_ops.exists(op))
        missed.push_back(op.name());
    end

    if (missed.size() > 0) begin
      `uvm_error("COVERAGE",
                 $sformatf("Functional coverage error. Missed: %p", missed))
    end
    else begin
      `uvm_info("COVERAGE", "Covered all operations", UVM_LOW)
    end
  endfunction

  function ops_t op_to_enum(int op);
    case (op)
      1: return ADD;
      2: return AND;
      3: return XOR;
      4: return MUL;
      default: return ADD;
    endcase
  endfunction

endclass