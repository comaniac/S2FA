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
#include "j2faClass.h"

using namespace MarsProgramAnalysis;

static int tool_type = 0; //1=aocl
static int naive_aocl = 0; //1=aocl
static int count_memcpy = 0;

using namespace std;

map <string, j2faClass*> map2jClass;

void *GetTypebyDecl(void *decl) {
	SgNode *sg_var = (SgNode *) decl;
  SgInitializedName *sg_init = isSgInitializedName(sg_var);
  SgVariableDeclaration *sg_decl = isSgVariableDeclaration(sg_var);

  if (sg_init)
    return sg_init->get_type();
  else if (sg_decl)
    return sg_decl->get_variables()[0]->get_type();
}

int GetTypeSize(void *type) {
	SgArrayType *aryType = isSgArrayType((SgNode *) type);
	string typeClassName = ((SgType *) type)->class_name();

/*
	if (!typeClassName.compare("SgTypeInt"))
		return 4;
	else if (!typeClassName.compare("SgTypeBool"))
		return 1;
	else if (!typeClassName.compare("SgTypeChar"))
		return 1;
	else if (!typeClassName.compare("SgTypeDouble"))
		return 8;
	else if (!typeClassName.compare("SgTypeFloat"))
		return 4;
	else if (!typeClassName.compare("SgTypeLong"))
		return 8;
	else if (!typeClassName.compare("SgTypeShort"))
		return 2;
*/
	if (!typeClassName.compare("SgClassType")) {
		string className = "::" + ((SgClassType *) type)->get_name();
		if (map2jClass[className])
			return map2jClass[className]->GetSize();
		else
			return 0;
	}
	else if (!typeClassName.compare("SgArrayType")) {
		SgIntVal *aryIdx = isSgIntVal(aryType->get_index());
		if (aryIdx == NULL) {
			cerr << "Array length cannot be a variable" << endl;
			return -1;
		}
		return GetTypeSize(aryType->get_base_type()) * aryIdx->get_value();
	}
	else if (!typeClassName.compare("SgPointerType")) {
		// We don't handle pointer here
		return 0;
	}

	return 1;
}

void GetNewExpsByName(CSageCodeGen & codegen, string funName, void *sg_scope,
                                      vector<void *> &vec_news) {
  vector<void *> all_news;
  codegen.GetNodesByType(sg_scope, "preorder", "SgNewExp", &all_news);

  for (int i = 0; i < all_news.size(); i++) {	
		SgNewExp *newExp = isSgNewExp((SgNode *) all_news[i]);
		SgClassDeclaration *newDecl = newExp->get_constructor_args()->get_class_decl();
		string newName = newDecl->get_qualified_name().getString();
		if (!newName.compare("::" + funName)) {
      vec_news.push_back(all_news[i]);
    }
  }
}

int InferArraySize(CSageCodeGen & codegen, void * pTopFunc, void * sgClass, void * sgVarDecl) {
	string varName = codegen.GetVariableName(((SgVariableDeclaration *) sgVarDecl));

	map<SgMemberFunctionDeclaration*, int> mapFun2ArgIdx;

	// Step 1: Find the assignment of target member variable
	SgDeclarationStatementPtrList &mList = ((SgClassDefinition *) sgClass)->get_members();
	SgDeclarationStatementPtrList::iterator mite = mList.begin();
	for (; mite != mList.end(); mite++) {
		SgMemberFunctionDeclaration *fun = isSgMemberFunctionDeclaration(*mite);
		if (!fun)
			continue;
		SgBasicBlock *body = isSgBasicBlock(((SgNode *) codegen.GetFuncBody(fun)));
		if (!body)
			continue;

		// Search for the assignment statement with target member variable as LHS
		vector<void *> vecTemp;
		codegen.GetNodesByType(body, "preorder", "SgVarRefExp", &vecTemp);
		for (int i = 0; i < vecTemp.size(); i++) {
			SgVarRefExp *exp = isSgVarRefExp((SgNode *) vecTemp[i]);
			if (exp->isUsedAsLValue()) {
	    	string refName = isSgInitializedName(
	        (SgNode *) codegen.GetVariableInitializedName(exp))->
					get_qualified_name().getString();
				refName = refName.substr(refName.find_last_of(':') + 1);
				if (!varName.compare(refName)) {
					void *node = vecTemp[i];
					while (node && !isSgAssignOp((SgNode *) node))
						node = codegen.GetParent(node);
					if (!node)
						continue;
					SgVarRefExp *rhs = isSgVarRefExp(
						isSgAssignOp((SgNode *) node)->get_rhs_operand());
					if (!rhs)
						continue;
		    	void *initName = codegen.GetVariableInitializedName(rhs);
					
					// Assume target member variable is assigned by
					// an function argument directly
					if (!codegen.IsArgumentInitName(initName))
						continue;

					string rhsName = isSgInitializedName(
						(SgNode *) initName)->get_qualified_name().getString();
					SgDeclarationStatement *decl = isSgInitializedName(
						(SgNode *) initName)->get_declaration();
					SgFunctionParameterList *fpl = isSgFunctionParameterList(decl);
					SgInitializedNamePtrList nList = fpl->get_args();
					int idx = 0;
					SgInitializedNamePtrList::iterator nite = nList.begin();
					for (; nite != nList.end(); nite++) {
						SgInitializedName *argName = *nite;
	         	if (!rhsName.compare(argName->unparseToString())) {
							mapFun2ArgIdx[fun] = idx;
							break;
						}
						idx++;
					}
				}
			}
		}
	}

	// Step 2: Find the function call for the corresponding variable
	// and fetch array length
	int typeSize = 0;
	vector<void*> vecFuncs;
	codegen.GetNodesByType(pTopFunc, "preorder",  "SgFunctionDeclaration", &vecFuncs);
	for (int i = 0; i < vecFuncs.size(); i++) {
		void *body = codegen.GetFuncBody(vecFuncs[i]);
		if (!body)
			continue;
		string sFuncName = codegen.GetFuncName(vecFuncs[i]);
		map<SgMemberFunctionDeclaration*, int>::iterator fite = mapFun2ArgIdx.begin();
		for (; fite != mapFun2ArgIdx.end(); fite++) {
			string tFuncName = codegen.GetFuncName(fite->first);

			// TODO: Now only search for "new" expression
			vector<void *> vecNewExps;
			GetNewExpsByName(codegen, tFuncName, body, vecNewExps);
			for (int j = 0; j < vecNewExps.size(); j++) {
				SgNewExp *exp = isSgNewExp((SgNode *) vecNewExps[j]);
				SgExpressionPtrList &eList = exp->get_constructor_args()->get_args()->get_expressions();
				if (eList.size() < fite->second)
					continue;
//				string argName = codegen.UnparseToString(eList[fite->second]);
				void *varDecl = codegen.GetVariableDecl(eList[fite->second]);
				int size = GetTypeSize(GetTypebyDecl(varDecl));
				if (size > typeSize)
					typeSize = size;
			}
		}
	}
	return typeSize;
}

int j2fa_gen(CSageCodeGen & codegen, void * pTopFunc, CInputOptions options, int debug_gen,
                int keep_code = 0)
{
	// Step 1: Parse class definitions
	vector <void*> vecClasses;
	codegen.GetNodesByType(pTopFunc, "preorder", "SgClassDefinition", &vecClasses);
	for (int i = 0; i < vecClasses.size(); i++) {
		SgNode *sgDecl = (SgNode *) vecClasses[i];
		SgClassDefinition *sgClass = isSgClassDefinition(sgDecl);
		map2jClass[sgClass->get_qualified_name()] = new j2faClass(sgClass);
	}

	// Step 2: Serialize class member variables
	for (int i = 0; i < vecClasses.size() + 1; i++) {
		bool done = true;
		for (int i = 0; i < vecClasses.size(); i++) {
			SgNode *sgDecl = (SgNode *) vecClasses[i];
			SgClassDefinition *sgClass = isSgClassDefinition(sgDecl);
			j2faClass *jClass = map2jClass[sgClass->get_qualified_name()];

			// Serialize member variables
			SgDeclarationStatementPtrList &mList = sgClass->get_members();
			SgDeclarationStatementPtrList::iterator mite = mList.begin();
			for (; mite != mList.end(); mite++) {
				SgVariableDeclaration *var = isSgVariableDeclaration(*mite);
				if (!var) // Ignore non-variables
					continue;

				string varName = codegen.GetVariableName(var);
				if (jClass->HasVariable(varName)) // Ignore known size variables
					continue;

				int typeSize = GetTypeSize(GetTypebyDecl(var));
				if (!typeSize) { // Need more information
					done = false;
					if (isSgPointerType((SgType *) GetTypebyDecl(var))) {
						typeSize = InferArraySize(codegen, pTopFunc, sgClass, var);
						if (!typeSize)
							cerr << "Cannot infer array size for " << codegen.UnparseToString(var) << endl;
					}
				}
				jClass->AddVariable(varName, typeSize);
			}

			// Calculate size
			int size = jClass->GetSize();
				
			// Update based classes (if any)
			SgBaseClassPtrList &bList = sgClass->get_inheritances();
			for (SgBaseClassPtrList::iterator bite = bList.begin(); bite != bList.end(); bite++) {
				string baseName = isSgBaseClass(*bite)->get_base_class()->get_qualified_name();
				j2faClass *bClass = map2jClass[baseName];
				bClass->SetSize(size);
			}
		}
		if (done)
			break;
	}

	for (int i = 0; i < vecClasses.size(); i++) {
		SgNode *sgDecl = (SgNode *) vecClasses[i];
		SgClassDefinition *sgClass = isSgClassDefinition(sgDecl);
		j2faClass *jClass = map2jClass[sgClass->get_qualified_name()];
		jClass->CalcIndex();

		cerr << sgClass->get_qualified_name() << ": ";
		cerr << map2jClass[sgClass->get_qualified_name()]->GetSize() << endl;
		jClass->DumpVariables();
	}

/*
		// Modify member functions
		for (SgDeclarationStatementPtrList::iterator mite = mList.begin(); 
			mite != mList.end(); mite++) {
			SgMemberFunctionDeclaration *fun = isSgMemberFunctionDeclaration(*mite);
			if (fun != NULL) {
			}
		}
*/

	cout << "J2FA transformation done" << endl;
	return 1;
}

