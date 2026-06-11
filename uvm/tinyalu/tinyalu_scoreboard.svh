class tinyalu_scoreboard extends uvm_scoreboard;
  `uvm_component_utils(tinyalu_scoreboard)

  uvm_tlm_analysis_fifo #(tinyalu_tx) item_collected;
  function new(string name, uvm_component parent);
    super.new(name, parent);
  endfunction : new

  function void build_phase(uvm_phase phase);
    super.build_phase(phase);
    item_collected = new("item_collected", this);
  endfunction : build_phase

  virtual function void check_phase(uvm_phase phase);
    tinyalu_tx tx;
    logic [15:0] predicted_result;
    int failed = 0;
    int count = 0;
    while (item_collected.can_get()) begin
      item_collected.try_get(tx);
      count++;
      predicted_result = predict(tx);
      if (predicted_result !== tx.result) begin
        failed++;
        `uvm_error(get_type_name(), $sformatf(
                "Mismatch for transaction: A=%0h, B=%0h, op=%0d. Predicted result: %0h, Received result: %0h",
                tx.A, tx.B, tx.op, predicted_result, tx.result));
      end
    end
    `uvm_info(get_type_name(), $sformatf("Checked %0d transactions.", count), UVM_LOW);
    if (failed == 0) begin
      `uvm_info(get_type_name(), "All transactions matched expected results.", UVM_LOW);
    end else begin
      `uvm_info(get_type_name(), $sformatf("%0d transactions failed.", failed), UVM_LOW);
    end
  endfunction : check_phase


  function logic [15:0] predict(tinyalu_tx tx);
    case (tx.op)
      1: return (tx.A + tx.B) & 16'hffff; // ADD
      2: return (tx.A & tx.B) & 16'hffff; // AND
      3: return (tx.A ^ tx.B) & 16'hffff; // XOR
      4: return (tx.A * tx.B) & 16'hffff; // MUL
      default: return 16'hxxxx;
    endcase

  endfunction : predict

endclass : tinyalu_scoreboard
