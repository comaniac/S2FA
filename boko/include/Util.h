// File Name    : Util.h
// Author       : Cody Hao Yu
// Creation Date: 2015 09 11 23:13 
// Last Modified: 2015 09 12 00:35
#ifndef __UTIL_H__
#define __UTIL_H__
#include "clang/AST/ASTContext.h"
#include "clang/ASTMatchers/ASTMatchers.h"
#include "clang/ASTMatchers/ASTMatchFinder.h"
#include "clang/Rewrite/Core/Rewriter.h"
#include "clang/Rewrite/Frontend/Rewriters.h"

std::string writeIndent(int n);
std::string writeStmt(const clang::Stmt *stmt);
bool areSameVariable(const clang::ValueDecl *, const clang::ValueDecl *);
bool areSameStmt(const clang::Stmt *, const clang::Stmt *);
bool isVarInExpr(const clang::VarDecl *, const clang::Expr *);
std::string getFirstVarType(const clang::Stmt *);

#endif
