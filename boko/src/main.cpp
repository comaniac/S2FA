#include <iostream>
#include <fstream>
#include <memory>
#include <string>
#include <sstream>

#include "clang/AST/AST.h"
#include "clang/AST/ASTConsumer.h"
#include "clang/AST/RecursiveASTVisitor.h"
#include "clang/ASTMatchers/ASTMatchers.h"
#include "clang/ASTMatchers/ASTMatchFinder.h"
#include "clang/Frontend/CompilerInstance.h"
#include "clang/Frontend/FrontendActions.h"
#include "clang/Rewrite/Core/Rewriter.h"
#include "clang/Rewrite/Frontend/Rewriters.h"
#include "llvm/Support/Host.h"
#include "llvm/Support/raw_ostream.h"
#include "clang/Tooling/CommonOptionsParser.h"
#include "clang/Tooling/Tooling.h"

#include "LoopHandler.h"

using namespace std;
using namespace clang;
using namespace clang::ast_matchers;
using namespace clang::driver;
using namespace clang::tooling;
using namespace llvm;

static cl::OptionCategory BokoCategory("Blaze OpenCL Kernel Optimization");

class BokoASTConsumer : public ASTConsumer {

	private:
		MatchFinder Matcher;
		LoopPipelineInserterImpl LoopPipelineInserter;
		ReductionInserterImpl ReductionInserter;

	public:
	  BokoASTConsumer(ASTContext &C, Rewriter &R) : 
			LoopPipelineInserter(C, R), ReductionInserter(C, R) {
/*
			// Loop pipeline matcher
			Matcher.addMatcher(forStmt(
				hasIncrement(anyOf( // Case 1: <declRefExpr>++
					unaryOperator( 
						hasOperatorName("++"),
	          hasUnaryOperand(declRefExpr(to(
							varDecl(hasType(isInteger())).bind("incVarName")))
						) // end hasUnaryOperand
					), // end case 1: unaryOperator
					binaryOperator( // Case 2: <declRefExpr> = <declRefExpr> + 1
						hasOperatorName("="),
						hasLHS(ignoringParenImpCasts(declRefExpr(to(
							varDecl(hasType(isInteger())).bind("incVarName"))))
						), // end hasLHS
						hasRHS(
							binaryOperator(
								hasOperatorName("+"),
								anyOf(
									hasLHS(integerLiteral(equals(1))),
									hasRHS(integerLiteral(equals(1)))
								)
							)
						) // end hasRHS
					) // end case 2: binaryOperator
				)), // end anyOf, hasIncrement
				hasCondition(
					binaryOperator( // <declRefExpr> < <expr>
						hasOperatorName("<"),
						hasLHS(ignoringParenImpCasts(declRefExpr(to(
							varDecl(hasType(isInteger())).bind("condVarName"))))
						), // end hasLHS
						hasRHS(expr(hasType(isInteger())))
					) // end binaryOperator
				) // end hasCondition
			).bind("LoopPipeline"), &LoopPipelineInserter);
*/
			// Reduction matcher
			Matcher.addMatcher(forStmt(
				hasIncrement(anyOf( // Case 1: <declRefExpr>++
					unaryOperator( 
						hasOperatorName("++"),
	          hasUnaryOperand(declRefExpr(to(
							varDecl(hasType(isInteger())).bind("incVarName")))
						) // end hasUnaryOperand
					), // end case 1: unaryOperator
					binaryOperator( // Case 2: <declRefExpr> = <declRefExpr> + 1
						hasOperatorName("="),
						hasLHS(ignoringParenImpCasts(declRefExpr(to(
							varDecl(hasType(isInteger())).bind("incVarName"))))
						), // end hasLHS
						hasRHS(
							binaryOperator(
								hasOperatorName("+"),
								anyOf(
									hasLHS(integerLiteral(equals(1))),
									hasRHS(integerLiteral(equals(1)))
								)
							)
						) // end hasRHS
					) // end case 2: binaryOperator
				)), // end anyOf, hasIncrement
				hasCondition(
					binaryOperator( // <declRefExpr> < <expr>
						hasOperatorName("<"),
						hasLHS(ignoringParenImpCasts(declRefExpr(to(
							varDecl(hasType(isInteger())).bind("condVarName"))))
						), // end hasLHS
						hasRHS(expr(hasType(isInteger())))
					) // end binaryOperator
				), // end hasCondition
				hasBody(
					stmt(compoundStmt(
						has(
							binaryOperator(
								hasOperatorName("="),
								hasLHS(stmt().bind("sumLHS")),
								hasRHS(
									binaryOperator(
										hasLHS(ignoringParenImpCasts(stmt().bind("sumRHS_LHS"))),
										hasRHS(ignoringParenImpCasts(stmt().bind("sumRHS_RHS")))
									).bind("sumOp") // end binaryOperator
								) // end hasRHS
							) // end binaryOperator
						) // end has
					)) // end stmt
				) // end hasBody
			).bind("SumReduction"), &ReductionInserter);

		}

		virtual void HandleTranslationUnit(ASTContext &Context) override {
			Matcher.matchAST(Context);
		}
};

class BokoFrontendAction : public ASTFrontendAction {
	private:
		Rewriter TheRewriter;

	public:
		virtual void EndSourceFileAction() {
		  TheRewriter.getEditBuffer(TheRewriter.getSourceMgr().getMainFileID()).write(outs());
		}

		virtual unique_ptr<ASTConsumer> CreateASTConsumer(CompilerInstance &CI, StringRef file) {
			TheRewriter.setSourceMgr(CI.getSourceManager(), CI.getLangOpts());
			return make_unique<BokoASTConsumer>(CI.getASTContext(), TheRewriter);
		}
};


int main(int argc, const char **argv) {
	CommonOptionsParser op(argc, argv, BokoCategory);
	ClangTool Tool(op.getCompilations(), op.getSourcePathList());

	return Tool.run(newFrontendActionFactory<BokoFrontendAction>().get());
}

