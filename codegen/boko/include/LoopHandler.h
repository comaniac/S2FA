// File Name    : LoopHandler.h
// Author       : Cody Hao Yu
// Creation Date: 2015 09 10 12:11 
// Last Modified: 2015 09 11 23:20
 
#ifndef __LOOP_HANDLER__
#define __LOOP_HANDLER__
#include "clang/AST/ASTContext.h"
#include "clang/ASTMatchers/ASTMatchers.h"
#include "clang/ASTMatchers/ASTMatchFinder.h"
#include "clang/Rewrite/Core/Rewriter.h"
#include "clang/Rewrite/Frontend/Rewriters.h"

using namespace clang;
using namespace clang::ast_matchers;

class LoopPipelineInserterImpl : public MatchFinder::MatchCallback {
	public:
	  LoopPipelineInserterImpl(ASTContext &c, Rewriter &r) : 
			TheRewriter(r), Context(c) { ; }

	  virtual void run(const MatchFinder::MatchResult &Result);

	private:
	  Rewriter &TheRewriter;
		ASTContext &Context;
};

class ReductionInserterImpl : public MatchFinder::MatchCallback {
	public:
	  ReductionInserterImpl(ASTContext &c, Rewriter &r) : 
			TheRewriter(r), Context(c) { ; }

	  virtual void run(const MatchFinder::MatchResult &Result);

	private:
	  Rewriter &TheRewriter;
		ASTContext &Context;
};

#endif
