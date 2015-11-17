/*
 * This material is distributed under the GNU General Public License
 * Version 2. You may review the terms of this license at
 * http://www.gnu.org/licenses/gpl-2.0.html
 *
 * Copyright (c) 1995-2015, The R Core Team
 * Copyright (c) 2003, The R Foundation
 * Copyright (c) 2015, Oracle and/or its affiliates
 *
 * All rights reserved.
 */
#ifndef VARIABLE_DEFS_H_
#define VARIABLE_DEFS_H_

// The global variables that are assumed by the R FFI.
// N.B. Some variables become functions in FastR, see RInternals.h

/* Evaluation Environment */
//SEXP R_GlobalEnv;
SEXP R_EmptyEnv;
//SEXP R_BaseEnv;
//SEXP R_BaseNamespace;
//SEXP R_NamespaceRegistry;

//SEXP R_Srcref;

/* Special Values */
SEXP R_NilValue;
SEXP R_UnboundValue;
SEXP R_MissingArg;

/* Symbol Table Shortcuts */
SEXP R_Bracket2Symbol;   /* "[[" */
SEXP R_BracketSymbol;    /* "[" */
SEXP R_BraceSymbol;      /* "{" */
SEXP R_ClassSymbol;     /* "class" */
SEXP R_DeviceSymbol;     /* ".Device" */
SEXP R_DevicesSymbol;     /* ".Devices" */
SEXP R_DimNamesSymbol;   /* "dimnames" */
SEXP R_DimSymbol;     /* "dim" */
SEXP R_DollarSymbol;     /* "$" */
SEXP R_DotsSymbol;     /* "..." */
SEXP R_DropSymbol;     /* "drop" */
SEXP R_LastvalueSymbol;  /* ".Last.value" */
SEXP R_LevelsSymbol;     /* "levels" */
SEXP R_ModeSymbol;     /* "mode" */
SEXP R_NameSymbol;     /* "name" */
SEXP R_NamesSymbol;     /* "names" */
SEXP R_NaRmSymbol;     /* "na.rm" */
SEXP R_PackageSymbol;    /* "package" */
SEXP R_QuoteSymbol;     /* "quote" */
SEXP R_RowNamesSymbol;   /* "row.names" */
SEXP R_SeedsSymbol;     /* ".Random.seed" */
SEXP R_SourceSymbol;     /* "source" */
SEXP R_TspSymbol;     /* "tsp" */

SEXP R_dot_defined;      /* ".defined" */
SEXP R_dot_Method;       /* ".Method" */
SEXP R_dot_target;       /* ".target" */
SEXP R_NaString;	    /* NA_STRING as a CHARSXP */
SEXP R_BlankString;	    /* "" as a CHARSXP */

// Symbols not part of public API but used in FastR tools implementation
SEXP R_SrcrefSymbol;
SEXP R_SrcfileSymbol;

// logical constants
SEXP R_TrueValue;
SEXP R_FalseValue;
SEXP R_LogicalNAValue;

// Arith.h
double R_NaN;		/* IEEE NaN */
double R_PosInf;	/* IEEE Inf */
double R_NegInf;	/* IEEE -Inf */
double R_NaReal;	/* NA_REAL: IEEE */
int R_NaInt;	/* NA_INTEGER:= INT_MIN currently */

// from Defn.h
const char* R_Home;
const char* R_TempDir;

// various ignored flags and variables:
Rboolean R_Visible;
Rboolean R_Interactive;
Rboolean R_interrupts_suspended;
int R_interrupts_pending;
Rboolean mbcslocale;
Rboolean useaqua;
char OutDec = '.';
Rboolean utf8locale = FALSE;
Rboolean mbcslocale = FALSE;
Rboolean latin1locale = FALSE;
int R_dec_min_exponent = -308;

#endif /* VARIABLE_DEFS_H_ */
