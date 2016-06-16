//#ifndef SCOPLIB_INT_T_IS_LONGLONG
//# define SCOPLIB_INT_T_IS_LONGLONG
//#endif
//#include <scoplib/scop.h>

#include <iostream>
#include <fstream>
#include <sys/resource.h>

#include "xml_parser.h"
#include "cmdline_parser.h"
#include "file_parser.h"

#include "mars_opt.h"
#include "PolyModel.h"
#include "tldm_annotate.h"
#include "codegen.h"
#include "program_analysis.h"
//#include "ir_type.h"

#include <algorithm>
#define PRINT_INFO 1

#define DEBUG 0

// also defined in codegen.cpp
#define USE_LOWERCASE_NAME 0

const string CRITICAL_MESSAGE_REPORT_FILE("critical_message.rpt");

vector<string> graph_execute_seq;
set<string> dump_array;
set<string> graph_names;

int g_debug_mode;

void dump_critical_message(string pass_name, string level,
                           string message, int number)
{
	const string file_name = CRITICAL_MESSAGE_REPORT_FILE;

	string str_type = level.substr(0, 1);
	string str_num = "";
	if (number != -1)
		str_num = "-" + my_itoa(number);

	string new_msg = "%"+str_type+" ["+pass_name+str_num+"] "+ message;
	// split into multiple lines with each line is less than 80 characters
	string format_msg;
	size_t curr_pos = 0;
	bool first_line = true;
	if (new_msg.size() > 80) {
		while (curr_pos < new_msg.size()) {
			if (new_msg.size() - curr_pos < 80) {
				format_msg += "  " + new_msg.substr(curr_pos);
				break;
			}
			string raw_str = new_msg.substr(curr_pos, 80);
			size_t last_white_space = raw_str.find_first_of("\n");
			if (last_white_space == string::npos)
				last_white_space = raw_str.find_last_of(" ");
			string new_line;
			if (last_white_space != string::npos) {
				new_line = raw_str.substr(0, last_white_space + 1);
				curr_pos += last_white_space + 1;
			} else {
				new_line = raw_str;
				curr_pos += 80;
				last_white_space = new_msg.find_first_of(" \n", curr_pos);
				if (last_white_space != string::npos) {
					new_line += new_msg.substr(curr_pos, last_white_space - curr_pos + 1);
					curr_pos = last_white_space;
				} else {
					new_line += new_msg.substr(curr_pos);
					curr_pos = new_msg.size();
				}
			}
			if (new_line[new_line.size() - 1] != '\n')
				new_line += "\n";
			if (first_line) {
				format_msg = new_line;
				first_line = false;
			} else
				format_msg += "  " + new_line;
			curr_pos = new_msg.find_first_not_of(" \n", curr_pos);
		}
	} else
		format_msg = new_msg;

	if (str_type != "I") format_msg = "\n" + format_msg + "\n";

	append_string_into_file(format_msg, file_name);
}

int tldm_pragma_parse_one_term(string sTerm, int j, string & sParam, vector<string> & vecList, vector<string> & vecCond)
{
	sParam = "";
	vecList.clear();
	vecCond.clear();

	string sList;
	sParam = get_sub_delimited(sTerm, j, '='); j+=sParam.size()+1;
	sParam = FormatSpace(sParam);
	if (j >= sTerm.size()) return j;

	int j0 = j;
    int j0_bak = j0;
	sList = get_sub_delimited(sTerm, j, '\"'); j+=sList.size()+1; j0 = j;
	sList = get_sub_delimited(sTerm, j, '\"'); j+=sList.size()+1;
	//printf("\t %s=%s\n", sParam.c_str(), sList.c_str());
	if (j0 >= sTerm.size() || j > sTerm.size()) 
	{
        // ZP: 20150703: add support for no ""
        if (0) {
		    //printf("j0=%d, size=%d, j=%d.\n", j0, sTerm.size(), j);
		    printf("[mars_opt] ERROR: tldm pragma passing failed (quotation marks \"\" are missing for parameter %s).\n", sParam.c_str());
		    printf("\t#pragma %s\n\n", sTerm.c_str());
		    assert(0);
		    exit(0);
        }
        else {
	        sList = get_sub_delimited(sTerm, j, ' '); j+=sList.size()+1; j0 = j;
	        sList = get_sub_delimited(sTerm, j, ' '); j+=sList.size()+1;
        }
	}
	sList = FormatSpace(sList); 
	
	// 2013-10-08
	if (0)
	{
		sList = RemoveQuote(sList); 

		string sFirst = get_sub_delimited(sList, 0, ':');
		str_split(sFirst, ',', vecList);
		if (sFirst.size() + 1 < sList.size()) 
		{
			string sSecond = sList.substr(sFirst.size()+1);
			str_split(sSecond, ';', vecCond);
		}
	}
	else 
	{
		vecList.push_back(sList);
	}
	return j;
}


int is_cmost_pragma( string sFilter)
{
	return sFilter == "tldm" || sFilter == "cmost" || sFilter == "ACCEL";
}

int tldm_pragma_parse_whole(string sPragma, string & sFilter, string & sCmd, map<string, pair<vector<string>, vector<string> > > & mapParam)
{
	mapParam.clear();
	int j = 0;
	sFilter = get_sub_delimited(sPragma, j, ' '); j+=sFilter.size()+1;
    if (!is_cmost_pragma(sFilter)) return 0;
	//if (sFilter != "tldm" && sFilter != "cmost") { return 0; }
	sCmd = get_sub_delimited(sPragma, j, ' '); j+=sCmd.size()+1;

	vector<string> vec0, vec1;
	while (j < sPragma.size())
	{
		string sParam;
		j = tldm_pragma_parse_one_term(sPragma, j, sParam, vec0, vec1);

		pair<vector<string>, vector<string> > empty;
		mapParam[sParam] = empty;

		mapParam[sParam].first = vec0;
		mapParam[sParam].second = vec1;
	}

	return 1;
}


extern void j2fa_gen(CSageCodeGen & codegen, void * pTopFunc, CInputOptions options,
                       int debug_gen, int keep_code);

int extract_top(vector<string> & vecSrcList, string sTldmFile, string sTopFunc,
                CInputOptions options)
{
	int i, j, k;
	CSageCodeGen codegen; // Rose AST encaptulation
	void * sg_project = codegen.OpenSourceFile(vecSrcList); // Root pointer
	codegen.InitBuiltinTypes();
	// codegen.GeneratePDF();

	// 1. get top function
	void * pTopFunc = 0; //void * to encaptulate Rose type
	if ("" != sTopFunc) {
		vector<void*> vecFuncs;
		codegen.GetNodesByType(sg_project, "preorder",  "SgFunctionDeclaration", &vecFuncs);

		for (i = 0; i < vecFuncs.size(); i++) {
			string sFuncName = codegen.GetFuncName(vecFuncs[i]);

			if (codegen.GetFuncBody(vecFuncs[i]) && sFuncName == sTopFunc) {
				pTopFunc = vecFuncs[i];
				break;
			}
		}

		if (!pTopFunc) {
			printf("[mars_opt] ERROR: top function is set (%s), but not found in the source code.\n",
			       sTopFunc.c_str());
			assert(pTopFunc);
		}
	} else
		pTopFunc = sg_project;


	int is_keep_code = (options.get_option("-a") == "bfm") ? 1 : 0;

	// J2FA code transformation
	if ("j2fa" == options.get_option("-p")) {
		j2fa_gen(codegen, pTopFunc, options, XMD_GEN, is_keep_code);
	}
	else {
		printf("[mars_opt] ERROR: Unrecognized pass \"%s\"\n\n", options.get_option("-p").c_str());
		assert(0);
		exit(0);
	}

	//cout << "Code generation ..." << endl;
	codegen.GenerateCode();

	return 1;
}

int main(int argc, char *argv[])
{


	////////////////////////////////////////////////
	// Set stack size (default 7.4MB for gcc)
	const rlim_t kStackSize = 64 * 1024 * 1024;   // min stack size = 512 MB
	struct rlimit rl;
	int result;

	result = getrlimit(RLIMIT_STACK, &rl);
	if (result == 0) {
		if (rl.rlim_cur < kStackSize) {
			rl.rlim_cur = kStackSize;
			result = setrlimit(RLIMIT_STACK, &rl);
			if (result != 0) {
				fprintf(stderr, "setrlimit returned result = %d\n", result);
				exit(0);
			}
		}
	}
	try {
		int i;

		CInputOptions options;
		options.set_flag("-t", 1, 0);
		options.set_flag("-e", 1);
		options.set_flag("-p", 1);
		options.set_flag("-o", 1, 0);
		options.set_flag("-x", 1, 0);
		options.set_flag("-a", 20, 0);
		options.set_flag("-c", 1, 0);
		options.set_flag("", 100000, 2);


		if (!options.parse(argc, argv)) {
			string str_version = read_string_from_file(string(getenv("MARS_COMPILER_HOME"))
			                     + string("/VERSION"));
			vector<string> str_version_lines = str_split(str_version, "\n");

			//cout << str_version_lines[1] << endl;
			//cout << str_version_lines[2] << endl;

			string build_num  = "0"; //str_version_lines[1].substr(18);
			string build_date = "0"; //str_version_lines[2].substr(19);

			printf("*****************************************************\n");
			printf("   Merlin Source Code Optimizer \n");
			printf("   Build: %s \n", build_num.c_str());
			printf("   Data : %s \n", build_date.c_str());
			printf("*****************************************************\n");
			printf("\n");
			printf("Usage: mars_opt file0 file1 -e [cl|c] -p pass_name [-a arg0] [-a arg1] ... \n");
			printf("       file_list: C/openCL source file list \n");
			printf("\n");
			exit(0);
		}

		g_debug_mode = 0;
		if ("debug" == options.get_option_key_value("-a", "debug_mode")) {
			cout << "debug mode" << endl;
			g_debug_mode = 1;
		}

		string sTopFunc = options.get_option("-t");
		if (0 == options.get_option_num("-o")) options.add_option("-o", sTopFunc+".tldm");

		vector<string> vecSrcFileList;

		// No matter C or Cl files, we will change the file name to c file
		for (i = 1; i < options.get_option_num(""); i++) {
			string sFile = options.get_option("", i);
			string ext = sFile.substr(sFile.find(".") + 1);
			if (ext == "cl") {
				string sFileOrg = sFile;
				sFile = sFile.substr(0, sFile.find(".")) + ".c";
				string Cmd = "cp " + sFileOrg + " " + sFile;
				system(Cmd.c_str());

			}
			vecSrcFileList.push_back(sFile);
		}

		string sTldmFileName = options.get_option("-o", 0);

		// Parse input C file
		system("rm -rf rose_succeed");
		if ("cl" == options.get_option("-e"))
			throw std::runtime_error("Doesn't support OpenCL file");
		else
			extract_top(vecSrcFileList, sTldmFileName, sTopFunc, options);
		system("touch rose_succeed");

		for (i = 1; i < options.get_option_num(""); i++) {
			string sFile = options.get_option("", i);
			string ext = sFile.substr(sFile.find(".") + 1);
			if (ext == "cl") {
				string sCFile = sFile.substr(0, sFile.find(".")) + ".c";
				string Cmd = "rm -rf " + sFile;
				system(Cmd.c_str());
			}
		}

		tldm_clean_all_new_annotation();

	} catch (std::exception e) {
		std::cout << "Internal error." << e.what() << std::endl;
		exit(1);
	} catch (...) {
		std::cout << "Internal error." << std::endl;
		exit(1);
	}
	//printf("mars_opt finished normally.\n");
	//std:cout << "mars_opt finished normally.\n";
	return 0;
}

