#include "codegen.h"
#include <fstream>
#include <map>
#include <string>
#include <stdlib.h>
#include <stdio.h>
#include "mars_opt.h"

#include "xml_parser.h"
#include "cmdline_parser.h"
#include "file_parser.h"
#include "codegen.h"

#include "tldm_annotate.h"

#include "program_analysis.h"
using namespace MarsProgramAnalysis;

static int tool_type = 0; //1=aocl
static int naive_aocl = 0; //1=aocl
static int count_memcpy = 0;

using namespace std;

int j2fa_gen(CSageCodeGen & codegen, void * pTopFunc, CInputOptions options, int debug_gen,
                int keep_code = 0)
{
	// Do nothing
	return 1;
}

