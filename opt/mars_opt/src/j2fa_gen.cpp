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

//#define DEBUG_FUNC_TRACE_UP

using namespace MarsProgramAnalysis;

static int tool_type = 0; //1=aocl
static int naive_aocl = 0; //1=aocl
static int count_memcpy = 0;

using namespace std;

map <string, j2faClass*> map2jClass;

bool IsPrimitiveType(string type) {
	if (type.find("*") != std::string::npos)
		type = type.substr(0, type.length() - 2);
	if (type.find("[") != std::string::npos)
		type = type.substr(0, type.find("[") - 1);
	if (type == "int" || type == "float" || type == "double" ||
			type == "char"|| type == "byte"  || type == "void"   ||
			type == "long") {
		return true;
	}
	return false;
}

bool IsPrimitiveType(void *_type) {
	SgType *type = isSgType((SgNode *) _type);
	if (type)
		return IsPrimitiveType(type->unparseToString());
	return false;	
}

void *CopyInitializedName(CSageCodeGen &codegen, void *ori_init_name) {
	SgInitializedName *sg_init_name = isSgInitializedName((SgNode *) ori_init_name);
	if (sg_init_name == NULL)
		sg_init_name = isSgInitializedName(
			(SgNode *) codegen.GetVariableInitializedName(ori_init_name));
	assert(sg_init_name);

	SgInitializer *sg_initer = isSgInitializer(
		(SgNode *) codegen.GetInitializer(sg_init_name));

	string var_name = codegen.GetVariableName(sg_init_name);
	SgType *sg_type = sg_init_name->get_type();

  SgInitializedName *sg_new_init =
		SageBuilder::buildInitializedName(var_name, sg_type, sg_initer);
  return sg_new_init;
}

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
	if (typeClassName == "SgClassType") {
		string className = "::" + ((SgClassType *) type)->get_name();
		if (map2jClass[className])
			return map2jClass[className]->GetSize();
		else
			return 0;
	}
	else if (typeClassName == "SgArrayType") {
		SgIntVal *aryIdx = isSgIntVal(aryType->get_index());
		if (aryIdx == NULL) {
			cerr << "Array length cannot be a variable" << endl;
			return -1;
		}
		return GetTypeSize(aryType->get_base_type()) * aryIdx->get_value();
	}
	else if (typeClassName == "SgPointerType") {
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
		if (newName == ("::" + funName)) {
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
				if (varName == refName) {
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
	         	if (rhsName == argName->unparseToString()) {
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

	#ifdef DEBUG_FUNC_TRACE_UP
		cerr << "[j2fa_gen] Class member indexing for serialization" << endl;
	#endif

	for (int i = 0; i < vecClasses.size(); i++) {
		SgNode *sgDecl = (SgNode *) vecClasses[i];
		SgClassDefinition *sgClass = isSgClassDefinition(sgDecl);
		j2faClass *jClass = map2jClass[sgClass->get_qualified_name()];
		jClass->CalcIndex();

		#ifdef DEBUG_FUNC_TRACE_UP
		cerr << "  " << sgClass->get_qualified_name() << ": size ";
		cerr << map2jClass[sgClass->get_qualified_name()]->GetSize() << endl;
		jClass->DumpVariables();
		#endif
	}

	vector<void*> vecFuncs;
	codegen.GetNodesByType(pTopFunc, "preorder",  "SgFunctionDeclaration", &vecFuncs);
	void *pos;
	for (int i = 0; i < vecFuncs.size(); i++) {
		string sFuncName = codegen.GetFuncName(vecFuncs[i]);

		if (codegen.GetFuncBody(vecFuncs[i])) {
			pos = vecFuncs[i];
			break;
		}
	}

	// Step 3: Process class member functions
	for (int i = 0; i < vecClasses.size(); i++) {
		SgNode *sgDecl = (SgNode *) vecClasses[i];
		SgClassDefinition *sgClass = isSgClassDefinition(sgDecl);
		string className = sgClass->get_qualified_name().getString();
		j2faClass *jClass = map2jClass[className];
		className = className.substr(2);

		SgDeclarationStatementPtrList &mList = sgClass->get_members();
		for (SgDeclarationStatementPtrList::iterator mite = mList.begin(); 
			mite != mList.end(); mite++) {
			SgMemberFunctionDeclaration *fun = isSgMemberFunctionDeclaration(*mite);
			if (!fun)
				continue;

			vector<void *> vecParams;
			set<SgInitializedName *> setOriParams;

			// The first argument must be "this"
			// TODO: If the kernel has only float points, use "unsigned int" instead
			void *thisObj = codegen.CreateVariable("long *", "this");
			vecParams.push_back(thisObj);
			SgInitializedNamePtrList &tempList = fun->get_parameterList()->get_args();
			for (int j = 0; j < tempList.size(); j++) {
				void *newParam = CopyInitializedName(codegen, tempList[j]);

				// Class type -> serialized array
				if (!IsPrimitiveType(codegen.UnparseToString(tempList[j]->get_type()))) {
					SgType *longType = SageBuilder::buildLongType();
					SgType *longAryType = SageBuilder::buildPointerType(longType);
					isSgInitializedName((SgNode *) newParam)->set_type(longAryType);
				}
				vecParams.push_back(newParam);
				setOriParams.insert(tempList[j]);
			}

			// Return class object -> serialized array pointer
			// TODO: Returned primitive array needs to be cast?
			string returnType = codegen.UnparseToString(codegen.GetFuncReturnType(fun));
			if (!IsPrimitiveType(returnType))
				returnType = "long *";

			// Rename constructor to "init"
			string funName = codegen.GetFuncName(fun);
			if (className == funName)
				funName = "init";

			// Create and insert a standalone funciton
			void *newFunc = codegen.CreateFuncDecl(
				returnType, 
				className + "_" + funName, 
				vecParams, 
				codegen.GetGlobal(fun), 
				true);

			codegen.InsertStmt(newFunc, pos);

			cerr << "Generated function " << funName << endl;
			void *oriBody = codegen.GetFuncBody(fun);
			void *newBody = codegen.GetFuncBody(newFunc);
			void *body = codegen.CopyStmt(oriBody);
			vector<void*> vecRefs;
			codegen.GetNodesByType(body, "preorder", "SgVarRefExp", &vecRefs);
			for (int j = 0; j < vecRefs.size(); j++) {
				SgInitializedName *initName = isSgInitializedName(
					(SgNode *) codegen.GetVariableInitializedName(vecRefs[j]));

				if (setOriParams.find(initName) == setOriParams.end()) {
					void *access = codegen.GetParent(vecRefs[j]);

					// Ref class field (must be "this->var"), transform to index access
					if (isSgArrowExp((SgNode *) access)) {
						int idx = jClass->GetVariableIndex(codegen.UnparseToString(vecRefs[j]));
						vector<void *> idxs;

						// Array field needs to add an offset
						if (isSgPntrArrRefExp((SgNode *) codegen.GetParent(access))) {
							SgExpression *offsetExp = isSgPntrArrRefExp(
								(SgNode *) codegen.GetParent(access))->get_rhs_operand();
							idxs.push_back(codegen.CreateExp(V_SgAddOp, 
								codegen.CreateConst(&idx, V_SgIntVal),
								codegen.CopyExp(offsetExp)));	
							access = codegen.GetParent(access);
						}
						else
							idxs.push_back(codegen.CreateConst(&idx, V_SgIntVal));
						void *thisRef = codegen.CreateVariableRef(thisObj);
						void *newRef = codegen.CreateArrayRef(thisRef, idxs);
						codegen.ReplaceExp(access, newRef);
						cerr << "  Link field " << codegen.UnparseToString(vecRefs[j]);
						cerr << " to " << codegen.UnparseToString(newRef) << endl;
					}
					else // Ignore local variable
						cerr << "  Skip local " << codegen.UnparseToString(vecRefs[j]) << endl;
				}
				else {
					cerr << "  Link arg " << codegen.UnparseToString(vecRefs[j]) << endl;
					SgInitializedName *oriInit = *(setOriParams.find(initName));
					void *newInit = NULL;
					for (int k = 0; k < vecParams.size(); k++) {
						string argName = codegen.GetVariableName(vecParams[k]);
						if (argName == codegen.GetVariableName(oriInit)) {
							newInit = vecParams[k];
							break;
						}
					}
					void *newRef = codegen.CreateVariableRef(newInit);
					codegen.ReplaceExp(vecRefs[j], newRef);
				}
			}
			
			// Insert transformed statements to new function body
			for (int j = 0; j < codegen.GetChildStmtNum(body); j++)
				codegen.AppendChild(newBody, codegen.GetChildStmt(body, j));
		}
	}

	// Step 4: Modify function calls accordingly
	vector<void*> vecCalls;
	codegen.GetNodesByType(pTopFunc, "preorder",  "SgMemberFunctionRefExp", &vecCalls);
	for (int i = 0; i < vecCalls.size(); i++)	{
		SgMemberFunctionRefExp *funRef = isSgMemberFunctionRefExp((SgNode *) vecCalls[i]);

		SgArrowExp *exp = isSgArrowExp((SgNode *) codegen.GetParent(funRef));
		if (!exp)
			continue;

		SgFunctionCallExp *call = isSgFunctionCallExp((SgNode *) codegen.GetParent(exp));
		if (!call)
			continue;

		#ifdef DEBUG_FUNC_TRACE_UP
		cerr << "[j2fa_gen] Transform function call: " << codegen.UnparseToString(call) << endl;
		#endif

		// Reference object
		void *refObj = exp->get_lhs_operand();

		// Function name
		string funName = codegen.UnparseToString(funRef);
		SgType *clsType = isSgExpression((SgNode *) refObj)->get_type();
		if (isSgPointerType(clsType))
			clsType = isSgPointerType(clsType)->get_base_type();
		if (!isSgClassType(clsType)) {
			cerr << "[j2fa_gen] Unknown type: " << codegen.UnparseToString(clsType) << ": ";
			cerr << clsType->class_name() << endl;
			return 0;
		}
		funName = isSgClassType(clsType)->get_name().getString() + "_" + funName;
		#ifdef DEBUG_FUNC_TRACE_UP
		cerr << "  New name: " << funName << endl;
		#endif

		// Parameters (newRefObj, original params)
		vector<void *> vecParams;
		void *newRefObj = codegen.CopyExp(refObj);
		vecParams.push_back(newRefObj);
		SgExpressionPtrList &aList = call->get_args()->get_expressions();
		for (int j = 0; j < aList.size(); j++)
			vecParams.push_back(codegen.CopyExp(aList[j]));
		
		// Return type
		void *retType = funRef->get_type();
		if (isSgMemberFunctionType((SgNode *) retType))
			retType = isSgMemberFunctionType((SgNode *) retType)->get_return_type();
		else {
			cerr << "[j2fa_gen] Unknown type: " << codegen.UnparseToString(retType) << ": ";
			cerr << isSgType((SgNode *) retType)->class_name() << endl;
			return 0;
		}
		#ifdef DEBUG_FUNC_TRACE_UP
		cerr << "  Return: " << codegen.UnparseToString(retType) << endl;
		cerr << "  New call: " << codegen.UnparseToString(newCall) << endl;
		#endif

		void *newCall = codegen.CreateFuncCall(funName, retType, vecParams, codegen.GetScope(call));
		
		codegen.ReplaceExp(call, newCall);
	}

	// Step 5: Change class type variables to the serialized type
	vector<void*> vecVars;
	codegen.GetNodesByType(pTopFunc, "preorder",  "SgVariableDeclaration", &vecVars);
	for (int i = 0; i < vecVars.size(); i++) {
		if (IsPrimitiveType(codegen.GetVariableTypeName(vecVars[i])))
			continue;

		string varName = codegen.GetVariableName(vecVars[i]);
		void *initExp = codegen.GetInitializerOperand(
			codegen.GetVariableInitializedName(vecVars[i]));

		// Create a variable with the serialized type to replace
		void *newVar = codegen.CreateVariableDecl(
			"long *", varName, NULL, codegen.GetScope(vecVars[i]));

		// Change constructor call to normal function call
		if (initExp) {
			if (!isSgNewExp((SgNode *) initExp)) {
				cerr << "Unexpected initial exp: ";
				cerr << codegen.UnparseToString(initExp) << endl;
				return 0;
			}
			SgNewExp *newExp = isSgNewExp((SgNode *) initExp);
			SgExpressionPtrList &newArgs = newExp->get_constructor_args()
				->get_args()->get_expressions();
			vector<void *> vecParams;
			vecParams.push_back(codegen.CreateVariableRef(newVar));
			for (int j = 0; j < newArgs.size(); j++)
				vecParams.push_back(newArgs[j]);
			string funName = isSgClassType(isSgPointerType(
				newExp->get_type())->get_base_type())->get_name().getString() + "_init";
			void *constCall = codegen.CreateFuncCall(
				funName, codegen.GetTypeByString("void"), vecParams, codegen.GetScope(vecVars[i]));
			void *initStmt = codegen.CreateStmt(V_SgExprStatement, constCall);
			codegen.InsertAfterStmt(initStmt, vecVars[i]);
		}
		codegen.ReplaceStmt(vecVars[i], newVar);
	}

	// Step 2.5: Change class type arguments to the serialized type for functions
	vecFuncs.clear();
	codegen.GetNodesByType(pTopFunc, "preorder",  "SgFunctionDeclaration", &vecFuncs);
	for (int i = 0; i < vecFuncs.size(); i++) {
		// Return type
		SgFunctionDeclaration *fun = isSgFunctionDeclaration((SgNode *) vecFuncs[i]);
		if (!IsPrimitiveType(codegen.GetFuncReturnType(fun)))
			codegen.SetFuncReturnType(fun, codegen.GetTypeByString("long *"));

		// Arguments
		SgInitializedNamePtrList &aList = fun->get_args();
		for (int j = 0; j < aList.size(); j++) {
			if (IsPrimitiveType(codegen.GetVariableTypeName(aList[j])))
				continue;
			isSgInitializedName((SgNode *) aList[j])->set_type(
				isSgType((SgNode *)codegen.GetTypeByString("long *")));
		}
	}


	// Remove class declarations FIXME: Why it doesn't work?
	for (int i = 0; i < vecClasses.size(); i++)
		codegen.RemoveStmt(vecClasses[i]);

	cout << "J2FA transformation done" << endl;
	return 1;
}

