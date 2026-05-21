CWD=$(shell pwd)
export COCOTB_REDUCED_LOG_FMT = 1
SIM ?= verilator
TOPLEVEL_LANG ?= verilog

EXTRA_ARGS += --trace-fst


VERILOG_SOURCES =$(CWD)/../../src/verilog/tinyalu.sv

ifeq ($(SIM), verilator)
    # Enable processing of #delay statements
    #COMPILE_ARGS += --timing
endif
MODULE := testbench
TOPLEVEL = tinyalu
GHDL_ARGS := --ieee=synopsys
COCOTB_HDL_TIMEUNIT = 1us
COCOTB_HDL_TIMEPRECISION = 1us
include $(shell cocotb-config --makefiles)/Makefile.sim
