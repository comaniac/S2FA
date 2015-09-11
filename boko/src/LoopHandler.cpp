// File Name    : LoopHandler.cpp
// Author       : Cody Hao Yu
// Creation Date: 2015 09 10 12:05 
// Last Modified: 2015 09 11 11:42
#include <list>
#include "LoopHandler.h"

using namespace llvm;
using namespace clang;
using namespace clang::ast_matchers;

static bool areSameVariable(const ValueDecl *First, const ValueDecl *Second) {
	return 	First && Second &&
					First->getCanonicalDecl() == Second->getCanonicalDecl();
}

static bool areSameStmt(const Stmt *First, const Stmt *Second) {

	if (!First || !Second)
		return false;

	std::list<const Stmt*> FirstQueue, SecondQueue;

	for (auto fite = First->child_begin(); fite != First->child_end(); ++fite)
		FirstQueue.push_back(*fite);
	for (auto site = Second->child_begin(); site != Second->child_end(); ++site)
		SecondQueue.push_back(*site);

	while (!FirstQueue.empty() && !SecondQueue.empty()) {
		const Stmt *FirstStmt = FirstQueue.front();
		FirstQueue.pop_front();
		const Stmt *SecondStmt = SecondQueue.front();
		SecondQueue.pop_front();

		errs() << FirstStmt->getStmtClassName() << " vs. " << SecondStmt->getStmtClassName() << " --> ";
		if (strcmp(FirstStmt->getStmtClassName(), SecondStmt->getStmtClassName())) {
			errs() << "false\n";
			errs() << "end false: diff =====================\n";
			return false;
		}
		errs() << "true\n";

		for (auto fite = FirstStmt->child_begin(); fite != FirstStmt->child_end(); ++fite)
			FirstQueue.push_back(*fite);
		for (auto site = SecondStmt->child_begin(); site != SecondStmt->child_end(); ++site)
			SecondQueue.push_back(*site);

		if (FirstQueue.size() != SecondQueue.size()) {
			errs() << "end false: size =====================\n";
			return false;
		}
	}

	errs() << "end true =====================\n";
	return true;
}

void LoopPipelineInserterImpl::run(const MatchFinder::MatchResult &Result) {

	// FIXME: Need a smarter way to add this pragma, so does loop unrolling.
	const ForStmt *FS = Result.Nodes.getStmtAs<ForStmt>("LoopPipeline");
	TheRewriter.InsertText(FS->getLocStart(), 
		"/* Boko */ __attribute__((xcl_loop_pipeline))\n", false, true);

	return ;
}

void ReductionInserterImpl::run(const MatchFinder::MatchResult &Result) {
	const ForStmt *loop = Result.Nodes.getStmtAs<ForStmt>("SumReduction");
	const VarDecl *incVar = Result.Nodes.getNodeAs<VarDecl>("incVarName");
	const Stmt *stmtLHS = Result.Nodes.getNodeAs<Stmt>("sumLHS");
	const Stmt *stmtRHS_LHS = Result.Nodes.getNodeAs<Stmt>("sumRHS_LHS");
	const Stmt *stmtRHS_RHS = Result.Nodes.getNodeAs<Stmt>("sumRHS_RHS");
	bool isValueAtLHS = true;

	// Test if LHS uses the loop variable.
	if (isa<ArraySubscriptExpr>(stmtLHS)) {
		const ArraySubscriptExpr *aryExpr = cast<ArraySubscriptExpr>(stmtLHS);
		const Expr *aryIdx = aryExpr->getIdx()->IgnoreParenImpCasts();
		if (isa<DeclRefExpr>(aryIdx)) {
			const ValueDecl *aryIdxVar = (cast<DeclRefExpr>(aryIdx))->getDecl();
			if (areSameVariable(aryIdxVar, incVar)) {
				TheRewriter.InsertText(loop->getLocStart(), 
				"/* Boko Loop with no reduction since loop variable used */\n", false, true);	
				return ;
			}
		}
	}

	// Test if it has the same variable on both sides
	if (areSameStmt(stmtLHS, stmtRHS_LHS)) {
		TheRewriter.InsertText(loop->getLocStart(), 
			"/* Boko Loop with reduction a = a + <stmt> */\n", false, true);
		isValueAtLHS = false;
	}
	else if (areSameStmt(stmtLHS, stmtRHS_RHS)) {
		TheRewriter.InsertText(loop->getLocStart(), 
			"/* Boko Loop with reduction a = <stmt> + a */\n", false, true);
	}
	else {
		TheRewriter.InsertText(loop->getLocStart(), 
			"/* Boko Loop with no reduction */\n", false, true);
		return ;
	}
	
//	const Stmt *stmtValue = (isValueAtLHS)? stmtRHS_RHS: stmtRHS_LHS;

//	TheRewriter.InsertText(stmtLHS->getLocStart(), "/* LHS */", false, true);
//	TheRewriter.InsertText(stmtRHS_LHS->getLocStart(), "/* RHS_LHS */", false, true);
//	TheRewriter.InsertText(stmtRHS_RHS->getLocStart(), "/* RHS_RHS */", false, true);

	return ;
}
