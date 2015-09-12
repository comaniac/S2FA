// File Name    : Util.cpp
// Author       : Cody Hao Yu
// Creation Date: 2015 09 11 23:14 
// Last Modified: 2015 09 12 00:34
#include <list>
#include "Util.h"
#include "llvm/Support/raw_ostream.h"

using namespace llvm;
using namespace clang;
using namespace clang::ast_matchers;

std::string writeIndent(int n) 
{
	std::string tabs = "";
	
	for (int i = 0; i < n; ++i)
		tabs += "\t";

	return tabs;
}

std::string writeStmt(const Stmt *stmt)
{
	std::string str;
	raw_string_ostream rso(str);
	LangOptions LangOpts;
	PrintingPolicy Policy(LangOpts);

	stmt->printPretty(rso, NULL, Policy, 0);

	return rso.str();
}

bool areSameVariable(const ValueDecl *First, const ValueDecl *Second) 
{
	return 	First && Second &&
					First->getCanonicalDecl() == Second->getCanonicalDecl();
}

bool areSameStmt(const Stmt *First, const Stmt *Second) 
{

	if (!First || !Second)
		return false;

	std::list<const Stmt*> FirstQueue, SecondQueue;

	FirstQueue.push_back(First);
	SecondQueue.push_back(Second);

	for (auto fite = First->child_begin(); fite != First->child_end(); ++fite)
		FirstQueue.push_back(*fite);
	for (auto site = Second->child_begin(); site != Second->child_end(); ++site)
		SecondQueue.push_back(*site);

	while (!FirstQueue.empty() && !SecondQueue.empty()) {

		// Ignore implicit and parenExpr
		while (!FirstQueue.empty() && 
					(isa<ImplicitCastExpr>(FirstQueue.front()) || isa<ParenExpr>(FirstQueue.front()))) {
			const Stmt *tempStmt = FirstQueue.front();

			// Push to the front of the queue to consist the order
			std::list<const Stmt*> tempQueue;
			for (auto tite = tempStmt->child_begin(); tite != tempStmt->child_end(); ++tite)
				tempQueue.push_back(*tite);
			FirstQueue.pop_front();
			for (auto tite = tempQueue.rbegin(); tite != tempQueue.rend(); ++tite)
				FirstQueue.push_front(*tite);
		}
		while (!SecondQueue.empty() && 
					(isa<ImplicitCastExpr>(SecondQueue.front()) || isa<ParenExpr>(SecondQueue.front()))) {
			const Stmt *tempStmt = SecondQueue.front();

			// Push to the front of the queue to consist the order
			std::list<const Stmt*> tempQueue;
			for (auto tite = tempStmt->child_begin(); tite != tempStmt->child_end(); ++tite)
				tempQueue.push_back(*tite);
			SecondQueue.pop_front();
			for (auto tite = tempQueue.rbegin(); tite != tempQueue.rend(); ++tite)
				SecondQueue.push_front(*tite);
		}

		const Stmt *FirstStmt = FirstQueue.front();
		FirstQueue.pop_front();
		const Stmt *SecondStmt = SecondQueue.front();
		SecondQueue.pop_front();

		#ifdef DEBUG
		errs() << FirstStmt->getStmtClassName() << " vs. " << SecondStmt->getStmtClassName() << " --> ";
		#endif
		if (strcmp(FirstStmt->getStmtClassName(), SecondStmt->getStmtClassName())) {
			#ifdef DEBUG
			errs() << "false\n";
			errs() << "end false: diff =====================\n";
			#endif
			return false;
		}

		if (isa<DeclRefExpr>(FirstStmt)) {
			if (!areSameVariable((cast<DeclRefExpr>(FirstStmt))->getDecl(), (cast<DeclRefExpr>(SecondStmt))->getDecl())) {
				#ifdef DEBUG
				errs() << "false\n";
				errs() << "end false: diff =====================\n";
				#endif
				return false;
			}
		}
		else if (isa<BinaryOperator>(FirstStmt)) {
			if ((cast<BinaryOperator>(FirstStmt))->getOpcode() != (cast<BinaryOperator>(SecondStmt))->getOpcode()) {
				#ifdef DEBUG
				errs() << "false\n";
				errs() << "end false: diff =====================\n";
				#endif
				return false;
			}
		}
		#ifdef DEBUG
		errs() << "true\n";
		#endif

		for (auto fite = FirstStmt->child_begin(); fite != FirstStmt->child_end(); ++fite)
			FirstQueue.push_back(*fite);
		for (auto site = SecondStmt->child_begin(); site != SecondStmt->child_end(); ++site)
			SecondQueue.push_back(*site);

		if (FirstQueue.size() != SecondQueue.size()) {
			#ifdef DEBUG
			errs() << "end false: size =====================\n";
			#endif
			return false;
		}
	}

	#ifdef DEBUG
	errs() << "end true =====================\n";
	#endif
	return true;
}

bool isVarInExpr(const VarDecl *var, const Expr *expr) 
{

	if (!var || !expr)
		return false;

	std::list<const Stmt*> Queue;
	Queue.push_back(cast<Stmt>(expr));

	while (!Queue.empty()) {
		const Stmt *tempStmt = Queue.front();
		Queue.pop_front();

		if (isa<DeclRefExpr>(tempStmt)) {
			if (areSameVariable((cast<DeclRefExpr>(tempStmt))->getDecl(), var))
				return true;
		}

		for (auto tite = tempStmt->child_begin(); tite != tempStmt->child_end(); ++tite)
		  Queue.push_back(*tite);
	}

	return false;
}

std::string getFirstVarType(const Stmt *stmt)
{
	if (!stmt)
		return NULL;

	std::list<const Stmt*> Queue;
	Queue.push_back(stmt);

	while (!Queue.empty()) {
		const Stmt *tempStmt = Queue.front();
		Queue.pop_front();

		if (isa<DeclRefExpr>(tempStmt)) {
				const ValueDecl *decl = (cast<DeclRefExpr>(tempStmt))->getDecl();
				const Type *type = cast<Type>(decl->getType());
				if (!type->isBuiltinType())
					return NULL; // Not support non-builtin types.

				const BuiltinType *bType = cast<BuiltinType>(type);
				LangOptions LangOpts;
				PrintingPolicy Policy(LangOpts);
				
				return bType->getName(Policy);
		}

		for (auto tite = tempStmt->child_begin(); tite != tempStmt->child_end(); ++tite)
		  Queue.push_back(*tite);
	}

	return NULL;
}

