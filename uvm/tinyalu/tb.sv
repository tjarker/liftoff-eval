package tinyalu_pkg;

    import uvm_pkg::*;
    `include "uvm_macros.svh"

    typedef enum logic [2:0] {
        ADD = 3'd0,
        AND_OP = 3'd1,
        XOR_OP = 3'd2,
        MUL = 3'd3
    } operation_t;

    class alu_seq_item extends uvm_sequence_item;

        rand bit [7:0] A;
        rand bit [7:0] B;
        rand operation_t op;

        bit [15:0] result;

        `uvm_object_utils(alu_seq_item)

        function new(string name = "alu_seq_item");
            super.new(name);
        endfunction

        function string convert2string();
            return $sformatf(
                "A=0x%02h B=0x%02h op=%0d result=0x%04h",
                A, B, op, result
            );
        endfunction

    endclass

    class alu_sequence extends uvm_sequence #(alu_seq_item);

        `uvm_object_utils(alu_sequence)

        function new(string name = "alu_sequence");
            super.new(name);
        endfunction

        task body();

            alu_seq_item item;

            repeat (20) begin

                item = alu_seq_item::type_id::create("item");

                start_item(item);

                // Avoid randomize() for maximal Verilator compatibility
                item.A  = $urandom_range(0, 255);
                item.B  = $urandom_range(0, 255);
                item.op = operation_t'($urandom_range(0, 3));

                finish_item(item);

            end

        endtask

    endclass

    class alu_driver extends uvm_driver #(alu_seq_item);

        `uvm_component_utils(alu_driver)

        virtual tinyalu_if vif;

        function new(string name, uvm_component parent);
            super.new(name, parent);
        endfunction

        task run_phase(uvm_phase phase);

            alu_seq_item item;

            forever begin

                seq_item_port.get_next_item(item);

                @(posedge vif.clk);

                vif.start <= 1'b1;
                vif.A     <= item.A;
                vif.B     <= item.B;
                vif.op    <= item.op;

                @(posedge vif.clk);

                vif.start <= 1'b0;

                wait(vif.done);

                item.result = vif.result;

                `uvm_info(
                    "DRIVER",
                    item.convert2string(),
                    UVM_MEDIUM
                )

                seq_item_port.item_done();

            end

        endtask

    endclass

    class alu_monitor extends uvm_component;

        `uvm_component_utils(alu_monitor)

        virtual tinyalu_if vif;

        mailbox #(alu_seq_item) mon_mb;

        function new(string name, uvm_component parent);
            super.new(name, parent);
        endfunction

        task run_phase(uvm_phase phase);

            alu_seq_item item;

            forever begin

                @(posedge vif.done);

                item = new();

                item.A      = vif.A;
                item.B      = vif.B;
                item.op     = operation_t'(vif.op);
                item.result = vif.result;

                mon_mb.put(item);

                `uvm_info(
                    "MONITOR",
                    item.convert2string(),
                    UVM_MEDIUM
                )

            end

        endtask

    endclass

    class alu_scoreboard extends uvm_component;

        `uvm_component_utils(alu_scoreboard)

        mailbox #(alu_seq_item) mon_mb;

        function new(string name, uvm_component parent);
            super.new(name, parent);
        endfunction

        task run_phase(uvm_phase phase);

            alu_seq_item item;
            bit [15:0] expected;

            forever begin

                mon_mb.get(item);

                case(item.op)

                    ADD:
                        expected = item.A + item.B;

                    AND_OP:
                        expected = item.A & item.B;

                    XOR_OP:
                        expected = item.A ^ item.B;

                    MUL:
                        expected = item.A * item.B;

                    default:
                        expected = '0;

                endcase

                if (expected != item.result) begin

                    `uvm_error(
                        "SCOREBOARD",
                        $sformatf(
                            "Mismatch expected=0x%04h actual=0x%04h",
                            expected,
                            item.result
                        )
                    )

                end
                else begin

                    `uvm_info(
                        "SCOREBOARD",
                        $sformatf(
                            "PASS expected=0x%04h",
                            expected
                        ),
                        UVM_LOW
                    )

                end

            end

        endtask

    endclass

    class alu_env extends uvm_env;

        `uvm_component_utils(alu_env)

        uvm_sequencer #(alu_seq_item) seqr;
        alu_driver                    drv;
        alu_monitor                   mon;
        alu_scoreboard                sb;

        mailbox #(alu_seq_item) mon_mb;

        virtual tinyalu_if vif;

        function new(string name, uvm_component parent);
            super.new(name, parent);
        endfunction

        function void build_phase(uvm_phase phase);

            super.build_phase(phase);

            seqr = uvm_sequencer#(alu_seq_item)::type_id::create(
                "seqr", this
            );

            drv = alu_driver::type_id::create(
                "drv", this
            );

            mon = alu_monitor::type_id::create(
                "mon", this
            );

            sb = alu_scoreboard::type_id::create(
                "sb", this
            );

            mon_mb = new();

            drv.vif = vif;
            mon.vif = vif;

            mon.mon_mb = mon_mb;
            sb.mon_mb  = mon_mb;

        endfunction

        function void connect_phase(uvm_phase phase);

            drv.seq_item_port.connect(seqr.seq_item_export);

        endfunction

    endclass

    class alu_test extends uvm_test;

        `uvm_component_utils(alu_test)

        alu_env env;
        alu_sequence seq;

        virtual tinyalu_if vif;

        function new(string name, uvm_component parent);
            super.new(name, parent);
        endfunction

        function void build_phase(uvm_phase phase);

            super.build_phase(phase);

            env = alu_env::type_id::create(
                "env", this
            );

            env.vif = vif;

        endfunction

        task run_phase(uvm_phase phase);

            phase.raise_objection(this);

            seq = alu_sequence::type_id::create("seq");

            seq.start(env.seqr);

            #100ns;

            phase.drop_objection(this);

        endtask

    endclass

endpackage