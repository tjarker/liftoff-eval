
#export UVM_HOME="$(pwd)/1800.2-2017-1.0/src"

get-uvm:
	wget https://www.accellera.org/images/downloads/standards/uvm/UVM-1800.2-2020.3.1.tar.gz
	tar -xvzf UVM-1800.2-2020.3.1.tar.gz
	

comp:
	verilator -Wno-fatal -Wno-WIDTHTRUNC -Wno-WIDTHEXPAND -Wno-IGNOREDRETURN --prof-exec --prof-cfuncs --binary -j 14 -O3 --trace-fst --top-module tinyalu_tb \
    +incdir+${UVM_HOME} +define+UVM_NO_DPI +incdir+$(pwd) -Wno-TIMESCALEMOD \
    ${UVM_HOME}/uvm_pkg.sv tinyalu_tb.sv ../../src/verilog/tinyalu.sv

run:
	./obj_dir/Vtinyalu_tb +UVM_TESTNAME=tinyalu_test