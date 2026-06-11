class tinyalu_sequence extends uvm_sequence #(tinyalu_tx);
  `uvm_object_utils(tinyalu_sequence)

  function new(string name = "tinyalu_sequence");
    super.new(name);
  endfunction

  virtual task body();
    for (int i = 0; i < 10; i++) begin
      req = tinyalu_tx::type_id::create("req");
      wait_for_grant();
      req.randomize();
      send_request(req);
      wait_for_item_done();
    end
  endtask

endclass


class random_sequence extends tinyalu_sequence;
  `uvm_object_utils(random_sequence)

  function new(string name = "random_sequence");
    super.new(name);
  endfunction

  virtual task body();
    for (int i = 1; i <= 4; i++) begin
      req = tinyalu_tx::type_id::create("req");
      wait_for_grant();
      req.randomize();
      req.op = i; // Set the operation to a specific value (1, 2, 3, or 4)
      send_request(req);
      wait_for_item_done();
    end
  endtask

endclass

class max_sequence extends tinyalu_sequence;
  `uvm_object_utils(max_sequence)

  function new(string name = "max_sequence");
    super.new(name);
  endfunction

  virtual task body();
    for (int i = 1; i <= 4; i++) begin
      req = tinyalu_tx::type_id::create("req");
      wait_for_grant();
      req.A = 8'hFF; // Max value for A
      req.B = 8'hFF; // Max value for B
      req.op = i; // Set the operation to a specific value (1, 2, 3, or 4)
      send_request(req);
      wait_for_item_done();
    end
  endtask

endclass


class fib_sequence extends tinyalu_sequence;
  `uvm_object_utils(fib_sequence)

  function new(string name = "fib_sequence");
    super.new(name);
  endfunction

  virtual task body();
    int unsigned a = 0, b = 1;
    int unsigned temp;
    for (int i = 0; i < 7; i++) begin
      req = tinyalu_tx::type_id::create("req");
      wait_for_grant();
      req.A = a;
      req.B = b;
      req.op = 1; // add operation
      send_request(req);
      wait_for_item_done();
      temp = a + b;
      a = b;
      b = temp;
    end
  endtask

endclass

// benchmark repeats random, max and fib sequences 1000 times
class bench_seq extends tinyalu_sequence;
  `uvm_object_utils(bench_seq)
  `uvm_declare_p_sequencer(tinyalu_sequencer)

  random_sequence rand_seq;
  max_sequence max_seq;
  fib_sequence fib_seq;

  function new(string name = "bench_seq");
    super.new(name);
  endfunction

  virtual task body();
    // get reps
    int reps;
    if (!uvm_config_db#(int)::get(null, "", "reps", reps))
      `uvm_fatal("NO_REPS", {"Reps parameter must be set for: ", get_full_name(), ". Reps should be set to the number of times to repeat the benchmark sequences."});
    for (int i = 0; i < reps; i++) begin
      rand_seq = random_sequence::type_id::create("rand_seq");
      max_seq = max_sequence::type_id::create("max_seq");
      fib_seq = fib_sequence::type_id::create("fib_seq");

      rand_seq.start(p_sequencer);
      max_seq.start(p_sequencer);
      fib_seq.start(p_sequencer);
    end
  endtask

endclass