# The source code is built against LLVM 3.7 and Clang 3.7

LLVM_SRC_PATH := $$HOME/clang-llvm-3.7/llvm-3.7.0.src
LLVM_BUILD_PATH := $$HOME/clang-llvm-3.7/build
LLVM_BIN_PATH := $(LLVM_BUILD_PATH)/bin

$(info -----------------------------------------------)
$(info Using LLVM_SRC_PATH = $(LLVM_SRC_PATH))
$(info Using LLVM_BUILD_PATH = $(LLVM_BUILD_PATH))
$(info Using LLVM_BIN_PATH = $(LLVM_BIN_PATH))
$(info -----------------------------------------------)

CXX := /curr/cody/.local/gcc-4.8/bin/g++
CXXFLAGS := -fno-rtti -O0 -g -fexceptions -Wunused-function
PLUGIN_CXXFLAGS := -fpic

LLVM_CXXFLAGS := `$(LLVM_BIN_PATH)/llvm-config --cxxflags`
LLVM_LDFLAGS := `$(LLVM_BIN_PATH)/llvm-config --ldflags --libs --system-libs`
LLVM_LDFLAGS_NOLIBS := `$(LLVM_BIN_PATH)/llvm-config --ldflags`
PLUGIN_LDFLAGS := -shared

CLANG_INCLUDES := \
	-I$(LLVM_SRC_PATH)/tools/clang/include \
	-I$(LLVM_BUILD_PATH)/tools/clang/include

CLANG_LIBS := \
	-Wl,--start-group \
	-lclangAST \
	-lclangAnalysis \
	-lclangBasic \
	-lclangDriver \
	-lclangEdit \
	-lclangFrontend \
	-lclangFrontendTool \
	-lclangLex \
	-lclangParse \
	-lclangSema \
	-lclangEdit \
	-lclangASTMatchers \
	-lclangRewrite \
	-lclangRewriteFrontend \
	-lclangStaticAnalyzerFrontend \
	-lclangStaticAnalyzerCheckers \
	-lclangStaticAnalyzerCore \
	-lclangSerialization \
	-lclangToolingCore \
	-lclangTooling \
	-Wl,--end-group

#	=====================
INCLUDE := ../include
BUILDDIR := ../bin
TARGET := Boko

SRC_OBJ = Util.o \
					LoopHandler.o \
					main.o

.PHONY: all
all: make_builddir $(BUILDDIR)/$(TARGET)

.PHONY: make_builddir
make_builddir:
	@test -d $(BUILDDIR) || mkdir $(BUILDDIR)

$(BUILDDIR)/$(TARGET): $(SRC_OBJ)
	$(CXX) $(CXXFLAGS) $(LLVM_CXXFLAGS) $(CLANG_INCLUDES) $^ \
		$(CLANG_LIBS) $(LLVM_LDFLAGS) -I$(INCLUDE) -o $@

%.o: %.cpp
	$(CXX) $(CXXFLAGS) $(LLVM_CXXFLAGS) $(CLANG_INCLUDES) -c $< \
		$(CLANG_LIBS) $(LLVM_LDFLAGS) -I$(INCLUDE) -o $@

.PHONY: clean
clean:
	rm -rf *.o $(BUILDDIR)/*
