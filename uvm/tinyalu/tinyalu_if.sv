interface tinyalu_if(input logic clk, input logic reset_n);

    logic        start;
    logic [7:0]  A;
    logic [7:0]  B;
    logic [2:0]  op;

    logic        done;
    logic [15:0] result;

endinterface