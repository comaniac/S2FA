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
	if (type.find("const") != std::string::npos)
		type = type.substr(6, type.length());
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

void *CreateInitializedName(CSageCodeGen &codegen, string var_name, void *sg_type_, void *sg_initer_ = NULL) {
	SgInitializer *sg_initer = isSgInitializer((SgNode *) sg_initer_);
	SgType *sg_type = isSgType((SgNode *) sg_type_);

  SgInitializedName *sg_new_init =
		SageBuilder::buildInitializedName(var_name, sg_type, sg_initer);
  return sg_new_init;
}


// Note: We ignore the function body duplication here.
// The copied function declaration will have an empty SgBasicBlock as a body.
void *CopyFuncDecl(CSageCodeGen &codegen, void *ori_decl) {
	SgFunctionDeclaration *fun = isSgFunctionDeclaration((SgNode *) ori_decl);
	vector<void *> vecParams;

	SgInitializedNamePtrList &tempList = fun->get_parameterList()->get_args();
	for (int j = 0; j < tempList.size(); j++) {
		void *newParam = CopyInitializedName(codegen, tempList[j]);
		vecParams.push_back(newParam);
	}

	string funName = codegen.GetFuncName(ori_decl);
	string returnType = codegen.UnparseToString(codegen.GetFuncReturnType(fun));
	void *newFunc = codegen.CreateFuncDecl(
		returnType, funName, vecParams, codegen.GetGlobal(fun), true);
	return newFunc;
}

bool IsVarRefForUse(CSageCodeGen &codegen, void *ref) {
	void *node = ref;
	void *parent = codegen.GetParent(ref);

	while (parent != NULL) {
		if (isSgAssignOp((SgNode *) parent)) {
			if (isSgAssignOp((SgNode *) parent)->get_lhs_operand() == node)
				return false;
			else
				return true;
		}
		else if (isSgStatement((SgNode *) parent)) {
			return true;
		}
		else if (isSgPntrArrRefExp((SgNode *) parent)) {
			if (isSgPntrArrRefExp((SgNode *) parent)->get_rhs_operand() == node)
				return true;
		}
		else if (isSgDotExp((SgNode *) parent)) {
			// FIXME: Why "this->obj_type_var =" is DotExp?
			return false;
		}
		else if (isSgCallExpression((SgNode *) parent)) {
			return true;
		}
		node = parent;
		parent = codegen.GetParent(parent);
	}

	assert(0);
}

void GetBaseClasses(void *sg_class, vector<void *> sg_base) {
	SgNode *sgDecl = (SgNode *) sg_class;
	SgClassDefinition *sgClass = isSgClassDefinition(sgDecl);
	SgBaseClassPtrList &vecBases = sgClass->get_inheritances();
	for (int i = 0; i < vecBases.size(); i++) {
		SgClassDefinition *baseClass = vecBases[i]->get_base_class()->get_definition();
		sg_base.push_back(baseClass);
	}
}

string PruneClassTypeName(string typeName_) {
	string typeName = typeName_;

	if (!strncmp(typeName.c_str(), "class", 5)) {
		typeName = typeName.substr(8, typeName.length());
		if (typeName.find("{") != std::string::npos)
			typeName = typeName.substr(0, typeName.find("{"));
	}
	return typeName;
}

// Note: Add class type name support
string GetVariableTypeName(CSageCodeGen &codegen, void *sg_var_) {
	string typeName = codegen.GetVariableTypeName(sg_var_);
	return PruneClassTypeName(typeName);
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
	string serializedType = "int";
	int serializedTypeSize = 4;

	// Step 1.1: Parse class definitions and create J2FA class models
	vector <void*> vecClasses;
	codegen.GetNodesByType(pTopFunc, "preorder", "SgClassDefinition", &vecClasses);
	for (int i = 0; i < vecClasses.size(); i++) {
		SgNode *sgDecl = (SgNode *) vecClasses[i];
		SgClassDefinition *sgClass = isSgClassDefinition(sgDecl);
		map2jClass[sgClass->get_qualified_name()] = new j2faClass(sgClass, i);
	}

	// Step 1.2: Determine serialized class data type
	vector <void*> vecVars;
	codegen.GetNodesByType(pTopFunc, "preorder", "SgVariableDeclaration", &vecVars);
	for (int i = 0; i < vecVars.size(); i++) {
		string typeName = codegen.GetVariableTypeName(vecVars[i]);
		if (typeName == "double" || typeName == "long") {
			serializedType = "long";
			serializedTypeSize = 8;
			break;
		}
	}

	// Step 1.3: Build class inheritance relationships
	for (int i = 0; i < vecClasses.size(); i++) {
		SgNode *sgDecl = (SgNode *) vecClasses[i];
		SgClassDefinition *sgClass = isSgClassDefinition(sgDecl);
		j2faClass *jClass = map2jClass[sgClass->get_qualified_name()];

		SgBaseClassPtrList &vecBases = sgClass->get_inheritances();
		for (int j = 0; j < vecBases.size(); j++) {
			SgClassDeclaration *baseClass = vecBases[j]->get_base_class();
			string baseClassName = baseClass->get_qualified_name().getString();
			j2faClass *jBaseClass = map2jClass[baseClassName];
			jBaseClass->AddDerivedClass(jClass);
			cerr << "Class " << baseClassName << " has a derived class ";
			cerr << jClass->GetName() << endl;
		}
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
	void *posBeforeMainFunc;
	for (int i = 0; i < vecFuncs.size(); i++) {
		string sFuncName = codegen.GetFuncName(vecFuncs[i]);

		if (codegen.GetFuncBody(vecFuncs[i])) {
			posBeforeMainFunc = vecFuncs[i];
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
		bool isBaseClass = jClass->HasDerivedClass();

		SgDeclarationStatementPtrList &mList = sgClass->get_members();
		for (SgDeclarationStatementPtrList::iterator mite = mList.begin(); 
			mite != mList.end(); mite++) {
			SgMemberFunctionDeclaration *fun = isSgMemberFunctionDeclaration(*mite);
			if (!fun)
				continue;
			string funName = codegen.GetFuncName(fun);

			// Collect derived class implementations
			map <int, string> mapDerivedImpl;
			if (isBaseClass) {
				vector <j2faClass *> derived = jClass->GetDerivedClasses();
				for (int j = 0; j < derived.size(); j++) {
					if (derived[j]->HasFunc(funName))
						continue;
					mapDerivedImpl[derived[j]->GetID()] = 
						derived[j]->GetName() + "_" + funName;
					cerr << "Function " << funName << " has an implementation";
					cerr << " from class " << derived[j]->GetName() << endl;
				}
			}

			vector<void *> vecParams;
			set<SgInitializedName *> setOriParams;

			// The first argument must be "this"
			void *thisObj = codegen.CreateVariable(serializedType + " *", "this");
			vecParams.push_back(thisObj);
			SgInitializedNamePtrList &tempList = fun->get_parameterList()->get_args();
			for (int j = 0; j < tempList.size(); j++) {
				void *newParam = CopyInitializedName(codegen, tempList[j]);

				// Class type -> serialized array
				if (!IsPrimitiveType(codegen.UnparseToString(tempList[j]->get_type()))) {
					SgType *sType = NULL;
					if (serializedType == "int")
						sType = SageBuilder::buildIntType();
					else
						sType = SageBuilder::buildLongType();
					SgType *sAryType = SageBuilder::buildPointerType(sType);
					isSgInitializedName((SgNode *) newParam)->set_type(sAryType);
				}
				vecParams.push_back(newParam);
				setOriParams.insert(tempList[j]);
			}

			// Return class object -> serialized array pointer
			string returnType = codegen.UnparseToString(codegen.GetFuncReturnType(fun));
			if (!IsPrimitiveType(returnType))
				returnType = serializedType + " *";

			// Rename constructor to "init"
			if (className == funName)
				funName = "init";

			funName = className + "_" + funName;
			if (mapDerivedImpl.size())
				funName = "base_" + funName;

			#ifdef DEBUG_FUNC_TRACE_UP
			cerr << "[j2fa_gen] Generate function " << funName << endl;
			#endif

			// Create and insert a standalone funciton
			void *newFunc = codegen.CreateFuncDecl(
				returnType, 
				funName, 
				vecParams, 
				codegen.GetGlobal(fun), 
				true);

			codegen.InsertStmt(newFunc, posBeforeMainFunc);

			// Copy and tranform function body
			void *oriBody = codegen.GetFuncBody(fun);
			void *newBody = codegen.GetFuncBody(newFunc);
			void *body = codegen.CopyStmt(oriBody);
			vector<void*> vecRefs;
			codegen.GetNodesByType(body, "preorder", "SgVarRefExp", &vecRefs);
			for (int j = 0; j < vecRefs.size(); j++) {
				SgInitializedName *initName = isSgInitializedName(
					(SgNode *) codegen.GetVariableInitializedName(vecRefs[j]));

				// Process non-argument variable accessing (local var., class field)
				if (setOriParams.find(initName) == setOriParams.end()) {
					void *access = codegen.GetParent(vecRefs[j]);

					// Ref class field (must be "this->var"), transform to index access
					if (isSgArrowExp((SgNode *) access)) {
						int idx = jClass->GetVariableIndex(codegen.UnparseToString(vecRefs[j]));
						vector<void *> idxs;
						bool isUse = false;
						if (IsVarRefForUse(codegen, vecRefs[j]))
							isUse = true;

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

						// Type casting: ... = exp -> ... = *((T *) &exp)
						if (isUse) {
							bool isPointer = true;

							// Object access: &obj (obj type: class) -> obj (obj type: serialized type *)
							if (isSgAddressOfOp((SgNode *) codegen.GetParent(access)))
								access = codegen.GetParent(access);

							// Fetch type name to be cast
							string typeName = codegen.UnparseToString(codegen.GetTypeByExp(access));
							if (!IsPrimitiveType(typeName))
								typeName = serializedType + " *";
							else if (typeName.find("[") != std::string::npos)
								typeName = typeName.substr(0, typeName.find("[")) + "*";
							else if (typeName.find("*") == std::string::npos) {
								typeName = typeName + " *";
								isPointer = false;
							}

							newRef = codegen.CreateExp(V_SgAddressOfOp, newRef);
							newRef = codegen.CreateExp(V_SgCastExp,	newRef, codegen.GetTypeByString(typeName));
							if (!isPointer)
								newRef = codegen.CreateExp(V_SgPointerDerefExp, newRef);

							// Replace variable use with casting
							codegen.ReplaceExp(access, newRef);
						}
						else { // Def
							void *rhsExp = access;
							while (rhsExp && !isSgAssignOp((SgNode *) rhsExp) && !isSgFunctionCallExp((SgNode *) rhsExp))
								rhsExp = codegen.GetParent(rhsExp);
							assert(rhsExp);

							// Fetch the value to write
							if (isSgAssignOp((SgNode *) rhsExp))
								rhsExp = isSgAssignOp((SgNode *) rhsExp)->get_rhs_operand();
							else { // Member "function call"
								SgExprListExp *argListExp = isSgFunctionCallExp((SgNode *) rhsExp)->get_args();
								assert(argListExp);

								SgExpressionPtrList &args = argListExp->get_expressions();
								assert(args.size() == 1);
								
								rhsExp = args[0];
							}
							string typeName = codegen.UnparseToString(codegen.GetTypeByExp(rhsExp));

							void *newRhsExp = codegen.CopyExp(rhsExp);
							if (IsPrimitiveType(typeName)) {
								// Write a scalar value directly
								newRhsExp = codegen.CreateExp(V_SgAddressOfOp, newRhsExp);
								newRhsExp = codegen.CreateExp(V_SgCastExp,	newRhsExp, codegen.GetTypeByString(serializedType + " *"));
								newRhsExp = codegen.CreateExp(V_SgPointerDerefExp, newRhsExp);
								codegen.ReplaceExp(rhsExp, newRhsExp);
								codegen.ReplaceExp(access, newRef);
							}
							else {
								// Use memcpy to write an array
								int varSize = jClass->GetVariableSize(codegen.UnparseToString(vecRefs[j]));
								newRef = codegen.CreateExp(V_SgAddressOfOp, newRef);

								vector<void *> vecParams;
								vecParams.push_back(newRef); // Target
								vecParams.push_back(newRhsExp); // Source
								vecParams.push_back(codegen.CreateConst(varSize * serializedTypeSize)); // Size
								void *memcpyCall = codegen.CreateFuncCall("memcpy", codegen.GetTypeByString("void"), 
									vecParams, codegen.GetScope(newFunc));
								void *newStmt = codegen.CreateStmt(V_SgExprStatement, memcpyCall);
								void *stmt = codegen.TraceUpByTypeCompatible(access, V_SgStatement);
								codegen.ReplaceStmt(stmt, newStmt);
							}
						}

						#ifdef DEBUG_FUNC_TRACE_UP
						cerr << "  Link field " << codegen.UnparseToString(vecRefs[j]);
						cerr << " to " << codegen.UnparseToString(newRef) << endl;
						#endif
					}
					#ifdef DEBUG_FUNC_TRACE_UP
					else // Ignore local variable
						cerr << "  Skip local " << codegen.UnparseToString(vecRefs[j]) << endl;
					#endif
				}
				else {
					#ifdef DEBUG_FUNC_TRACE_UP
					cerr << "  Link arg " << codegen.UnparseToString(vecRefs[j]) << endl;
					#endif
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

			// Generate dispatcher function if necessary
			if (!mapDerivedImpl.size())
				continue;
			
			void *disFunc = CopyFuncDecl(codegen, newFunc);
			codegen.SetFuncName(disFunc, funName.substr(5));
			codegen.InsertStmt(disFunc, posBeforeMainFunc);
			void *funBody = codegen.GetFuncBody(disFunc);

			// Create selector for looking at class id (this[0])
			int idIdx = 0;
			vector <void *> idxs;
			idxs.push_back(codegen.CreateConst(&idIdx, V_SgIntVal));
			SgExpression *selector = isSgExpression((SgNode *) codegen.CreateArrayRef(
				codegen.CreateVariableRef(codegen.GetFuncParam(disFunc, 0)), idxs));

			// Create switch body
			SgBasicBlock *switchBody = isSgBasicBlock((SgNode *) codegen.CreateBasicBlock());
			map<int, string>::iterator dite = mapDerivedImpl.begin();
			for (; dite != mapDerivedImpl.end(); dite++) {
				vector <void *> vecArgs;
				for (int j = 0; j < codegen.GetFuncParamNum(disFunc); j++)
					vecArgs.push_back(codegen.CreateVariableRef(codegen.GetFuncParam(disFunc, j)));

				SgBasicBlock *caseBody = isSgBasicBlock((SgNode *) codegen.CreateBasicBlock());

				void *subFuncCall = codegen.CreateFuncCall(
					dite->second.substr(2), codegen.GetTypeByString(returnType), vecArgs, codegen.GetScope(funBody));

				void *retStmt = codegen.CreateStmt(V_SgReturnStmt, subFuncCall);
				codegen.AppendChild(caseBody, retStmt);

				SgCaseOptionStmt *caseStmt = SageBuilder::buildCaseOptionStmt(
					SageBuilder::buildIntVal(dite->first), caseBody);
				codegen.AppendChild(switchBody, caseStmt);
			}

			// Add itself as default
			vector <void *> vecArgs;
			for (int j = 0; j < codegen.GetFuncParamNum(disFunc); j++)
				vecArgs.push_back(codegen.CreateVariableRef(codegen.GetFuncParam(disFunc, j)));

			SgBasicBlock *caseBody = isSgBasicBlock((SgNode *) codegen.CreateBasicBlock());

			void *subFuncCall = codegen.CreateFuncCall(
				funName, codegen.GetTypeByString(returnType), vecArgs, codegen.GetScope(funBody));

			void *retStmt = codegen.CreateStmt(V_SgReturnStmt, subFuncCall);
			codegen.AppendChild(caseBody, retStmt);

			SgDefaultOptionStmt *caseStmt = SageBuilder::buildDefaultOptionStmt(caseBody);
			codegen.AppendChild(switchBody, caseStmt);
		
			// Build switch statement
			SgSwitchStatement* switchStmt = SageBuilder::buildSwitchStatement(selector, switchBody);
			selector->set_parent(switchStmt);
			switchBody->set_parent(switchStmt);
			
			codegen.AppendChild(funBody, switchStmt);

			// Dispatcher must be placed right before the actual main function
			// to avoid implicit declaration, so rest functions have to be 
			// put on the top of it.
			posBeforeMainFunc = disFunc;
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
		void *newCall = codegen.CreateFuncCall(funName, retType, vecParams, codegen.GetScope(call));
		#ifdef DEBUG_FUNC_TRACE_UP
		cerr << "  Return: " << codegen.UnparseToString(retType) << endl;
		cerr << "  New call: " << codegen.UnparseToString(newCall) << endl;
		#endif
		codegen.ReplaceExp(call, newCall);
	}

	// Step 5: Change class type variables to the serialized type
	vecVars.clear();
	codegen.GetNodesByType(pTopFunc, "preorder", "SgVariableDeclaration", &vecVars);
	for (int i = 0; i < vecVars.size(); i++) {
		if (IsPrimitiveType(codegen.GetVariableTypeName(vecVars[i])))
			continue;

		string varName = codegen.GetVariableName(vecVars[i]);
		void *initExp = codegen.GetInitializerOperand(
			codegen.GetVariableInitializedName(vecVars[i]));

		// Fetch serialized class size
		string classType = "::" + GetVariableTypeName(codegen, vecVars[i]);
		if (!map2jClass[classType])
			continue;
		int classSize = map2jClass[classType]->GetSize();

		// Create a variable with the serialized type to replace
		void *newVar = codegen.CreateVariableDecl(
			serializedType + " [" + std::to_string(classSize) + "]", "u_" + varName, NULL, codegen.GetScope(vecVars[i]));

		// Process variable initialization
		if (initExp) {
			// Change constructor call to normal function call
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
			for (int j = 0; j < newArgs.size(); j++) {
				void *argExp = codegen.CopyExp(newArgs[j]);
				string argTypeName = codegen.UnparseToString(codegen.GetTypeByExp(argExp));

				// Remove dereference for class type arguments due to type change
				// constructor(*obj) -> className_constructor(obj)
				// className *obj -> serializedType *obj
				// TODO: Apply the same transformation to normal function calls
				if (!IsPrimitiveType(argTypeName)) {
					SgConstructorInitializer *constInit = isSgConstructorInitializer((SgNode *) argExp);
					SgExpressionPtrList &_args = constInit->get_args()->get_expressions();
					assert (_args.size() == 1);
					if (isSgPointerDerefExp(_args[0])) {
						void *operand = codegen.GetExpUniOperand(_args[0]);
						_args[0] = isSgExpression((SgNode *) operand);
					}
				}
				vecParams.push_back(argExp);
			}
			string funName = isSgClassType(isSgPointerType(
				newExp->get_type())->get_base_type())->get_name().getString() + "_init";
			void *constCall = codegen.CreateFuncCall(
				funName, codegen.GetTypeByString("void"), vecParams, codegen.GetScope(vecVars[i]));
			void *initStmt = codegen.CreateStmt(V_SgExprStatement, constCall);
			codegen.InsertAfterStmt(initStmt, vecVars[i]);
		}
		codegen.ReplaceStmt(vecVars[i], newVar);

		// Relink reference
		vector<void *> vecVarRefs;
		codegen.GetNodesByType(pTopFunc, "preorder", "SgVarRefExp", &vecVarRefs);
		for (int j = 0; j < vecVarRefs.size(); j++) {
			if (codegen.GetVariableDecl(vecVarRefs[j]) == vecVars[i])
				codegen.ReplaceExp(vecVarRefs[j], codegen.CreateVariableRef(newVar));
		}
	}

	// Step 6: Change class type arguments to the serialized type for functions
	for (int i = 0; i < vecFuncs.size(); i++) {
		if (!codegen.GetFuncBody(vecFuncs[i]))
			continue;

		// Copy function
		SgFunctionDeclaration *fun = isSgFunctionDeclaration((SgNode *) vecFuncs[i]);
		string funName = codegen.GetFuncName(fun);

		bool returnObj = false;
		void *retArg = NULL;
		vector<void *> vecParams;
		set<SgInitializedName *> setOriParams;
		SgInitializedNamePtrList &tempList = fun->get_parameterList()->get_args();
		for (int j = 0; j < tempList.size(); j++) {
			void *newParam = CopyInitializedName(codegen, tempList[j]);

			// Transform class type arguments to serialized type
			if (!IsPrimitiveType(codegen.GetVariableTypeName(newParam))) {
				isSgInitializedName((SgNode *) newParam)->set_type(
				  isSgType((SgNode *)codegen.GetTypeByString(serializedType + " *")));
			}
			vecParams.push_back(newParam);
		}

		// Return type: If a function is trying to return an object, 
		// we transform it to return void and make it as an argument
		string returnType = codegen.UnparseToString(codegen.GetFuncReturnType(fun));
		if (!IsPrimitiveType(returnType)) {
			returnObj = true;
			returnType = "void";
			string varName = funName + "_return";
			retArg = CreateInitializedName(codegen, varName, codegen.GetTypeByString(serializedType + " *"));
			vecParams.push_back(retArg);
		}

		// Create a copy function
		void *newFun = codegen.CreateFuncDecl(
			returnType, 
			funName, 
			vecParams, 
			codegen.GetGlobal(fun), 
			true);

		codegen.ReplaceStmt(fun, newFun);

		void *oriBody = codegen.GetFuncBody(fun);
		void *newBody = codegen.GetFuncBody(newFun);
		void *body = codegen.CopyStmt(oriBody);
		vector<void*> vecRefs;
		codegen.GetNodesByType(body, "preorder", "SgVarRefExp", &vecRefs);
		for (int j = 0; j < vecRefs.size(); j++) {
			SgInitializedName *initName = isSgInitializedName(
				(SgNode *) codegen.GetVariableInitializedName(vecRefs[j]));

			// Process argument variable accessing
			if (setOriParams.find(initName) != setOriParams.end()) {
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

		// Return type: If a function is trying to return an object, 
		// we transform it to return void and make it as an argument
		if (returnObj) {
			vector<void *> vecRets;
			codegen.GetNodesByType(newFun, "preorder", "SgReturnStmt", &vecRets);
			assert(vecRets.size() == 1);
			SgReturnStmt *retStmt = isSgReturnStmt((SgNode *) vecRets[0]);
			SgExpression *retVal = retStmt->get_expression();
			assert(isSgVarRefExp(retVal));
			SgVariableDeclaration *retValDecl = isSgVariableDeclaration((SgNode *) codegen.GetVariableDecl(retVal));

			// Relink references
			vector<void *> vecVarRefs;
			codegen.GetNodesByType(newFun, "preorder", "SgVarRefExp", &vecVarRefs);
			for (int j = 0; j < vecVarRefs.size(); j++) {
				if (codegen.GetVariableDecl(vecVarRefs[j]) == retValDecl) {
					void *newRef = codegen.CreateVariableRef(retArg);
					codegen.ReplaceExp(vecVarRefs[j], newRef);
				}
			}

			// Remove return stmt
			codegen.RemoveStmt(retStmt);
			codegen.RemoveStmt(retValDecl);

			// Relink function calls
			vector<void *> vecFuncCalls;
			codegen.GetNodesByType(pTopFunc, "preorder", "SgFunctionCallExp", &vecFuncCalls);
			for (int j = 0; j < vecFuncCalls.size(); j++) {
				if (codegen.GetFuncNameByCall(vecFuncCalls[j]) != funName)
					continue;
				void *stmt = codegen.TraceUpByTypeCompatible(vecFuncCalls[j], V_SgStatement);

				void *lhsExp = NULL;
				if (!isSgExprStatement((SgNode *) stmt))
					continue;

				SgExpression *exp = isSgExprStatement((SgNode *) stmt)->get_expression();
				if (isSgAssignOp(exp))
					lhsExp = isSgAssignOp(exp)->get_lhs_operand();
				else if (isSgFunctionCallExp(exp)) { // FIXME: Why?
					SgDotExp *dotExp = isSgDotExp(exp->get_traversalSuccessorByIndex(0));
					if (!dotExp)
						continue;
					lhsExp = dotExp->get_lhs_operand();
				}
				cerr << "Transforming function call for returning objects: ";
				cerr << codegen.UnparseToString(lhsExp) << endl;

				void *newArg = codegen.CopyExp(lhsExp);
				newArg = codegen.CreateExp(V_SgAddressOfOp, newArg);
				
				vector<void *> vecParams;
				for (int k = 0; k < codegen.GetFuncCallParamNum(vecFuncCalls[j]); k++) {
					void *param = codegen.GetFuncCallParam(vecFuncCalls[j], k);
					vecParams.push_back(param);
				}
				vecParams.push_back(newArg);
				void *newCall = codegen.CreateFuncCall(
					funName, codegen.GetTypeByString("void"), vecParams, codegen.GetScope(vecFuncCalls[j]));
				void *newStmt = codegen.CreateStmt(V_SgExprStatement, newCall);	
				codegen.ReplaceStmt(stmt, newStmt);
			}
		}
	}

	// Step 7: Remove class declarations
	vector<void *> vecClassDecls;
	codegen.GetNodesByType(pTopFunc, "preorder", "SgClassDeclaration", &vecClassDecls);
	for (int i = 0; i < vecClassDecls.size(); i++)
		codegen.RemoveStmt(vecClassDecls[i]);

	cout << "J2FA transformation done" << endl;
	return 1;
}

