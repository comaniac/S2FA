// File Name    : LoopHandler.cpp
// Author       : Cody Hao Yu
// Creation Date: 2015 09 10 12:05 
// Last Modified: 2015 09 14 18:21
#include <list>
#include "LoopHandler.h"
#include "Util.h"

using namespace llvm;
using namespace clang;
using namespace clang::ast_matchers;

#define LoopUnrollHint std::string("__attribute__((opencl_unroll_hint))")
#define LoopPipelineHint std::string("__attribute__((xcl_pipeline_loop))")

void LoopPipelineInserterImpl::run(const MatchFinder::MatchResult &Result) {

	// FIXME: Need a smarter way to add this pragma, so does loop unrolling.
	const ForStmt *FS = Result.Nodes.getStmtAs<ForStmt>("LoopPipeline");
	TheRewriter.InsertText(FS->getLocStart(), 
		LoopPipelineHint + "\n", false, true);

	return ;
}

void ReductionInserterImpl::run(const MatchFinder::MatchResult &Result) {
	const ForStmt *loop = Result.Nodes.getStmtAs<ForStmt>("SumReduction");
	const VarDecl *incVar = Result.Nodes.getNodeAs<VarDecl>("incVarName");
	const Expr *condExpr = Result.Nodes.getNodeAs<Expr>("condBoundExpr");
	const Stmt *stmtLHS = Result.Nodes.getNodeAs<Stmt>("sumLHS");
	const Stmt *stmtRHS_LHS = Result.Nodes.getNodeAs<Stmt>("sumRHS_LHS");
	const Stmt *stmtRHS_RHS = Result.Nodes.getNodeAs<Stmt>("sumRHS_RHS");
	bool isValueAtLHS = true;
	int factor = 16; // FIXME: Should be determined cleverly

	// Test if LHS uses the loop variable.
	if (isa<ArraySubscriptExpr>(stmtLHS)) {
		const ArraySubscriptExpr *aryExpr = cast<ArraySubscriptExpr>(stmtLHS);
		const Expr *aryIdx = aryExpr->getIdx()->IgnoreParenImpCasts();

		if (isVarInExpr(incVar, aryIdx)) {
			TheRewriter.InsertText(loop->getLocStart(), 
			"/* Boko Loop with no reduction since loop variable used */\n", false, true);	
			return ;
		}
	}

	// Test if it has the same variable on both sides
	if (areSameStmt(stmtLHS, stmtRHS_LHS)) {
//		TheRewriter.InsertText(loop->getLocStart(), 
//			"/* Boko Loop with reduction a = a + <stmt> */\n", false, true);
		isValueAtLHS = false;
	}
	else if (areSameStmt(stmtLHS, stmtRHS_RHS)) {
//		TheRewriter.InsertText(loop->getLocStart(), 
//			"/* Boko Loop with reduction a = <stmt> + a */\n", false, true);
	}
	else {
		TheRewriter.InsertText(loop->getLocStart(), 
			"/* Boko Loop with no reduction */\n", false, true);
		return ;
	}
	
	const Stmt *stmtValue = (isValueAtLHS)? stmtRHS_LHS: stmtRHS_RHS;
	const BinaryOperator *op = Result.Nodes.getNodeAs<BinaryOperator>("sumOp");
	const std::string type = getFirstVarType(stmtLHS);

	// Modify loop increment from +1 to +factor
	std::string newLoopDef = writeStmt(cast<Stmt>(loop));

	// Step 1: Cut loop body
	newLoopDef = newLoopDef.substr(0, newLoopDef.find("{") + 1);

	// Step 2: Locating increment part starts from the 2nd semicolon
	std::size_t secondSemi = newLoopDef.find_first_of(";", newLoopDef.find_first_of(";") + 1);

	// Step 3: Remove increment
	newLoopDef = newLoopDef.substr(0, secondSemi + 1) + ") {";

	std::string newCode = newLoopDef + "\n";

	// Write reduction temp variables
	newCode += writeIndent(1) + type + " boko_dup[" + std::to_string(factor) + "];\n";

	// Write the first loop
	newCode += writeIndent(1) + LoopUnrollHint + "\n";
	newCode += writeIndent(1) + "for (int boko_idx = 0; boko_idx < " + std::to_string(factor) 
														+ "; ++boko_idx, ++" + incVar->getNameAsString() + ") {\n";
	newCode += writeIndent(2) + "if (" + incVar->getNameAsString() + " < " + writeStmt(cast<Stmt>(condExpr)) + ")\n";
	newCode += writeIndent(3) + "boko_dup[boko_idx] = " + writeStmt(stmtValue) + ";\n";
	newCode += writeIndent(2) + "else\n";
	newCode += writeIndent(3) + "boko_dup[boko_idx] = ";
	if (op->isAdditiveOp())
		newCode += "0;\n";
	else if (op->isMultiplicativeOp() || !op->getOpcodeStr().str().compare("/"))
		newCode += "1;\n";
	newCode += writeIndent(1) + "}\n";

	// Write the rest loops
	int step = 2;
	while (step < factor) {
		newCode += writeIndent(1) + LoopUnrollHint + "\n";
		newCode += writeIndent(1) + "for (int boko_idx = 0; boko_idx < " + std::to_string(factor)
															+ "; boko_idx += " + std::to_string(step) + ")\n";
		newCode += writeIndent(2) + "boko_dup[bok_idx] " + op->getOpcodeStr().str()
															+ "= boko_dup[boko_idx + " + std::to_string(step / 2) + "];\n\n";
		step *= 2;
	}
	
	// Write the final reduction
	newCode += writeIndent(1) + "boko_dup[0] " + op->getOpcodeStr().str()
														+ "= boko_dup[" + std::to_string(factor / 2) + "];\n";

	// Write the assignment
	newCode += writeIndent(1) + writeStmt(stmtLHS) + " " + op->getOpcodeStr().str() + "= boko_dup[0];\n";

	TheRewriter.InsertText(loop->getLocStart(), 
		newCode, false, true);

	// Comment the body of the original loop
	TheRewriter.InsertText(loop->getLocStart(), "/*\n", true, true);
	TheRewriter.InsertText(loop->getLocEnd(), "*/\n", true, true);

	return ;
}
