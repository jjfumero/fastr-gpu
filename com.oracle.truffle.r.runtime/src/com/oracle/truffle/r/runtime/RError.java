/*
 * This material is distributed under the GNU General Public License
 * Version 2. You may review the terms of this license at
 * http://www.gnu.org/licenses/gpl-2.0.html
 *
 * Copyright (c) 1995-2015, The R Core Team
 * Copyright (c) 2003, The R Foundation
 * Copyright (c) 2013, 2015, Oracle and/or its affiliates
 *
 * All rights reserved.
 */
package com.oracle.truffle.r.runtime;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.api.nodes.*;
import com.oracle.truffle.r.runtime.env.REnvironment.PutException;
import com.oracle.truffle.r.runtime.nodes.*;

/**
 * A facade for handling errors. This class extends {@link RuntimeException} so that it can be
 * thrown, and various static methods that should be used to actually effect the throw. It also
 * declares the {@link Message} enum that provides an abstract way to declare a particular message.
 *
 * The error messages in {@link Message} have been copied from GNU R source code.
 *
 * The details of the error handling, which is complicated by support for condition handling and the
 * ability to invoke arbitrary code, typically {@code browser}, is in {@link RErrorHandling}.
 *
 * In the event that an error is not "handled" or a warning is actually generated (they may be
 * delayed) it is necessary to construct a string that represents the "context", (using the GnuR
 * term) where the error occurred. GnuR maintains a physical {@code Context} objects to denote this,
 * but FastR does not, Instead we have information on which "builtin" reported the error/warning, by
 * way of a {@link Node} value (which may be indirectly related to the actual builtin due to AST
 * transformations) and the Truffle {@link Frame} stack. Mostly the {@link Node} value and
 * {@link Frame} are sufficient to reconstruct the context, but there are some special cases that
 * might require more information to disambiguate. Rather than create a new class to carry that, we
 * would simply create an instance of a {@link RBaseNode} subclass with the additional state.
 * Currently,there are no such cases.
 *
 */
@SuppressWarnings("serial")
public final class RError extends RuntimeException {

    private final String verboseStackTrace;

    /**
     * This exception should be subclassed by subsystems that need to throw subsystem-specific
     * exceptions to be caught by builtin implementations, which can then invoke
     * {@link RError#error(RBaseNode, RErrorException)}, which access the stored {@link Message}
     * object and any arguments. E.g. see {@link PutException}.
     */
    public abstract static class RErrorException extends Exception {
        private static final long serialVersionUID = 1L;

        private final RError.Message msg;
        @CompilationFinal private final Object[] args;

        @TruffleBoundary
        protected RErrorException(RError.Message msg, Object[] args) {
            super(RErrorHandling.formatMessage(msg, args), null);
            this.msg = msg;
            this.args = args;
        }
    }

    /**
     * This flags a call to {@code error} or {@code warning} will no value for {@link Node}. Ideally
     * this never happens, so we make it explicit.
     */
    public static final RBaseNode NO_NODE = new RBaseNode() {
    };

    /**
     * A very special case that ensures that no caller is output in the error/warning message. This
     * is needed where, even if there is a caller, GnuR does not show it.
     */
    public static final RBaseNode NO_CALLER = new RBaseNode() {
    };

    /**
     * This is a workaround for a case in {@code RCallNode} where an error might be thrown while
     * executing a {@code RootNode}, which is not a subclass of {@link RBaseNode}.
     */
    public static final RBaseNode ROOTNODE = new RBaseNode() {
    };

    /**
     * TODO the string is not really needed as all output is performed prior to the throw.
     */
    RError(String msg) {
        super(msg);
        verboseStackTrace = RInternalError.createVerboseStackTrace();
    }

    @Override
    public String toString() {
        return getMessage();
    }

    public String getVerboseStackTrace() {
        return verboseStackTrace;
    }

    @TruffleBoundary
    public static RError error(RBaseNode node, Message msg, Object... args) {
        throw error0(node, msg, args);
    }

    @TruffleBoundary
    public static RError error(Node node, Message msg, Object... args) {
        throw error0(findParentRBase(node), msg, args);
    }

    @TruffleBoundary
    public static RError error(RBaseNode node, Message msg) {
        throw error0(node, msg, (Object[]) null);
    }

    @TruffleBoundary
    public static RError error(Node node, Message msg) {
        throw error0(findParentRBase(node), msg, (Object[]) null);
    }

    private static RBaseNode findParentRBase(Node node) {
        Node current = node;
        while (current != null) {
            if (current instanceof RBaseNode) {
                return (RBaseNode) current;
            }
            current = current.getParent();
        }
        throw new AssertionError("Could not find RBaseNode for given Node. Is it not adopted in the AST?");
    }

    /**
     * Handles an R error with the most general argument signature. All other facade variants
     * delegate to this method.
     *
     * Note that the method never actually returns a result, but the throws the error directly.
     * However, the signature has a return type of {@link RError} to allow callers to use the idiom
     * {@code throw error(...)} to indicate the control transfer. It is entirely possible that, due
     * to condition handlers, the error will not actually be thrown.
     *
     *
     * @param node {@code RNode} of the code throwing the error, or {@link #NO_NODE} if not
     *            available. If {@code NO_NODE} an attempt will be made to identify the call context
     *            from the currently active frame.
     * @param msg a {@link Message} instance specifying the error
     * @param args arguments for format specifiers in the message string
     */
    @TruffleBoundary
    private static RError error0(RBaseNode node, Message msg, Object... args) {
        assert node != null;
        // thrown from a builtin specified by "node"
        RErrorHandling.signalError(node, msg, args);
        return RErrorHandling.errorcallDflt(node, msg, args);
    }

    /**
     * Convenience variant of {@link #error(RBaseNode, Message, Object...)} where only one argument
     * to the message is given. This avoids object array creation caller, which may be
     * Truffle-compiled.
     */
    @TruffleBoundary
    public static RError error(RBaseNode node, Message msg, Object arg) {
        throw error(node, msg, new Object[]{arg});
    }

    /**
     * Variant for the case where the original error occurs in code where it is not appropriate to
     * report the error. The error information is propagated using the {@link RErrorException}.
     */
    @TruffleBoundary
    public static RError error(RBaseNode node, RErrorException ex) {
        throw error(node, ex.msg, ex.args);
    }

    /**
     * A temporary error that indicates an unimplemented feature where terminating the VM using
     * {@link Utils#fatalError(String)} would be inappropriate.
     */
    @TruffleBoundary
    public static RError nyi(RBaseNode node, String msg) {
        throw error(node, RError.Message.NYI, msg);
    }

    @TruffleBoundary
    public static void warning(RBaseNode node, Message msg, Object... args) {
        assert node != null;
        RErrorHandling.warningcall(true, node, msg, args);
    }

    @TruffleBoundary
    public static void warning(Node node, Message msg, Object... args) {
        assert node != null;
        warning(findParentRBase(node), msg, args);
    }

    @TruffleBoundary
    public static RError stop(boolean showCall, RBaseNode node, Message msg, Object arg) {
        assert node != null;
        RErrorHandling.signalError(node, msg, arg);
        return RErrorHandling.errorcallDflt(showCall, node, msg, arg);
    }

    @TruffleBoundary
    public static void performanceWarning(String string) {
        if (FastROptions.PerformanceWarnings) {
            warning(RError.NO_NODE, Message.PERFORMANCE, string);
        }
    }

    public static enum Message {
        /**
         * Eventually this will go away, used only by {@link RError#nyi}.
         */
        NYI("not yet implemented: %s"),
        /**
         * {@code GENERIC} should only be used in the rare case where a known error is not
         * available.
         */
        GENERIC("%s"),
        ARG_RECYCYLED("an argument will be fractionally recycled"),
        LENGTH_GT_1("the condition has length > 1 and only the first element will be used"),
        LENGTH_ZERO("argument is of length zero"),
        NA_UNEXP("missing value where TRUE/FALSE needed"),
        LENGTH_NOT_MULTI("longer object length is not a multiple of shorter object length"),
        INTEGER_OVERFLOW("NAs produced by integer overflow"),
        NA_OR_NAN("NA/NaN argument"),
        SUBSCRIPT_BOUNDS("subscript out of bounds"),
        SUBSCRIPT_BOUNDS_SUB("[[ ]] subscript out of bounds"),
        SELECT_LESS_1("attempt to select less than one element"),
        SELECT_MORE_1("attempt to select more than one element"),
        ONLY_0_MIXED("only 0's may be mixed with negative subscripts"),
        REPLACEMENT_0("replacement has length zero"),
        NOT_MULTIPLE_REPLACEMENT("number of items to replace is not a multiple of replacement length"),
        MORE_SUPPLIED_REPLACE("more elements supplied than there are to replace"),
        NA_SUBSCRIPTED("NAs are not allowed in subscripted assignments"),
        INVALID_ARG_TYPE("invalid argument type"),
        INVALID_ARG_TYPE_UNARY("invalid argument to unary operator"),
        VECTOR_SIZE_NEGATIVE("vector size cannot be negative"),
        NO_LOOP_FOR_BREAK_NEXT("no loop for break/next, jumping to top level"),
        INVALID_FOR_SEQUENCE("invalid for() loop sequence"),
        NO_NONMISSING_MAX("no non-missing arguments to max; returning -Inf"),
        NO_NONMISSING_MIN("no non-missing arguments to min; returning Inf"),
        NO_NONMISSING_MAX_NA("no non-missing arguments, returning NA"),
        NO_NONMISSING_MIN_NA("no non-missing arguments, returning NA"),
        LENGTH_NONNEGATIVE("length must be non-negative number"),
        MUST_BE_POSITIVE("%s must be a non-negative number"),
        INVALID_TFB("invalid (to - from)/by in seq(.)"),
        WRONG_SIGN_IN_BY("wrong sign in 'by' argument"),
        WRONG_TYPE("wrong type of argument"),
        BY_TOO_SMALL("'by' argument is much too small"),
        INCORRECT_SUBSCRIPTS("incorrect number of subscripts"),
        INCORRECT_SUBSCRIPTS_MATRIX("incorrect number of subscripts on matrix"),
        NEGATIVE_EXTENTS_TO_MATRIX("negative extents to matrix"),
        INVALID_SEP("invalid 'sep' specification"),
        INVALID_LENGTH("invalid '%s' length"),
        EMPTY_WHAT("empty 'what' specified"),
        LINE_ELEMENTS("line %d did not have %d elements"),
        ITEMS_NOT_MULTIPLE("number of items read is not a multiple of the number of columns"),
        // below: GNU R gives also expression for the argument
        NOT_FUNCTION("argument is not a function, character or symbol"),
        NON_CHARACTER("non-character argument"),
        NON_NUMERIC_MATH("non-numeric argument to mathematical function"),
        NAN_PRODUCED("NaNs produced"),
        NUMERIC_COMPLEX_MATRIX_VECTOR("requires numeric/complex matrix/vector arguments"),
        NON_CONFORMABLE_ARGS("non-conformable arguments"),
        DATA_VECTOR("'data' must be of a vector type"),
        NON_NUMERIC_MATRIX_EXTENT("non-numeric matrix extent"),
        // below: also can mean empty
        INVALID_NCOL("invalid 'ncol' value (too large or NA)"),
        // below: also can mean empty
        INVALID_NROW("invalid 'nrow' value (too large or NA)"),
        NEGATIVE_NCOL("invalid 'ncol' value (< 0)"),
        NEGATIVE_NROW("invalid 'nrow' value (< 0)"),
        NON_CONFORMABLE_ARRAYS("non-conformable arrays"),
        UNKNOWN_UNNAMED_OBJECT("object not found"),
        ONLY_MATRIX_DIAGONALS("only matrix diagonals can be replaced"),
        REPLACEMENT_DIAGONAL_LENGTH("replacement diagonal has wrong length"),
        NA_INTRODUCED_COERCION("NAs introduced by coercion"),
        ARGUMENT_WHICH_NOT_LOGICAL("argument to 'which' is not logical"),
        X_NUMERIC("'x' must be numeric"),
        X_ARRAY_TWO("'x' must be an array of at least two dimensions"),
        ACCURACY_MODULUS("probable complete loss of accuracy in modulus"),
        INVALID_SEPARATOR("invalid separator"),
        INCORRECT_DIMENSIONS("incorrect number of dimensions"),
        LOGICAL_SUBSCRIPT_LONG("(subscript) logical subscript too long"),
        DECREASING_TRUE_FALSE("'decreasing' must be TRUE or FALSE"),
        ARGUMENT_LENGTHS_DIFFER("argument lengths differ"),
        ZERO_LENGTH_PATTERN("zero-length pattern"),
        ALL_CONNECTIONS_IN_USE("all connections are in use"),
        CANNOT_READ_CONNECTION("cannot read from this connection"),
        CONNECTION_NOT_OPEN_READ("connection not open for reading"),
        CONNECTION_NOT_OPEN_WRITE("connection not open for writing"),
        BINARY_CONNECTION_REQUIRED("binary-mode connection required for ascii=FALSE"),
        CANNOT_WRITE_CONNECTION("cannot write to this connection"),
        ONLY_READ_BINARY_CONNECTION("can only read from a binary connection"),
        ONLY_WRITE_BINARY_CONNECTION("can only write to a binary connection"),
        NOT_A_TEXT_CONNECTION("'con' is not a textConnection"),
        UNSEEKABLE_CONNECTION("'con' is not seekable"),
        MORE_CHARACTERS("more characters requested than are in the string - will zero-pad"),
        TOO_FEW_LINES_READ_LINES("too few lines read in readLineWRITE_ONs"),
        INVALID_CONNECTION("invalid connection"),
        OUT_OF_RANGE("out-of-range values treated as 0 in coercion to raw"),
        UNIMPLEMENTED_COMPLEX("unimplemented complex operation"),
        UNIMPLEMENTED_COMPLEX_FUN("unimplemented complex function"),
        COMPARISON_COMPLEX("invalid comparison with complex values"),
        NON_NUMERIC_BINARY("non-numeric argument to binary operator"),
        RAW_SORT("raw vectors cannot be sorted"),
        INVALID_UNNAMED_ARGUMENT("invalid argument"),
        INVALID_UNNAMED_VALUE("invalid value"),
        NAMES_NONVECTOR("names() applied to a non-vector"),
        ONLY_FIRST_VARIABLE_NAME("only the first element is used as variable name"),
        INVALID_FIRST_ARGUMENT("invalid first argument"),
        NO_ENCLOSING_ENVIRONMENT("no enclosing environment"),
        ASSIGN_EMPTY("cannot assign values in the empty environment"),
        USE_NULL_ENV_DEFUNCT("use of NULL environment is defunct"),
        REPLACEMENT_NOT_ENVIRONMENT("replacement object is not an environment"),
        ARGUMENT_NOT_MATRIX("argument is not a matrix"),
        DOLLAR_ATOMIC_VECTORS("$ operator is invalid for atomic vectors"),
        COERCING_LHS_TO_LIST("Coercing LHS to a list"),
        ARGUMENT_NOT_LIST("argument not a list"),
        DIMS_CONTAIN_NEGATIVE_VALUES("the dims contain negative values"),
        NEGATIVE_LENGTH_VECTORS_NOT_ALLOWED("negative length vectors are not allowed"),
        FIRST_ARG_MUST_BE_ARRAY("invalid first argument, must be an array"),
        IMAGINARY_PARTS_DISCARDED_IN_COERCION("imaginary parts discarded in coercion"),
        DIMS_CONTAIN_NA("the dims contain missing values"),
        LENGTH_ZERO_DIM_INVALID("length-0 dimension vector is invalid"),
        ATTRIBUTES_LIST_OR_NULL("attributes must be a list or NULL"),
        RECALL_CALLED_OUTSIDE_CLOSURE("'Recall' called from outside a closure"),
        MATCH_CALL_CALLED_OUTSIDE_FUNCTION("match.call() was called from outside a function"),
        NOT_NUMERIC_VECTOR("argument is not a numeric vector"),
        UNSUPPORTED_PARTIAL("unsupported options for partial sorting"),
        INDEX_RETURN_REMOVE_NA("'index.return' only for 'na.last(NA'"),
        SUPPLY_X_Y_MATRIX("supply both 'x' and 'y' or a matrix-like 'x'"),
        SD_ZERO("the standard deviation is zero"),
        INVALID_UNNAMED_ARGUMENTS("invalid arguments"),
        INVALID_INPUT("invalid input"),
        INVALID_INPUT_TYPE("invalid input type"),
        NA_PRODUCED("NAs produced"),
        DETERMINANT_COMPLEX("determinant not currently defined for complex matrices"),
        NON_NUMERIC_ARGUMENT("non-numeric argument"),
        FFT_FACTORIZATION("fft factorization error"),
        COMPLEX_NOT_PERMITTED("complex matrices not permitted at present"),
        FIRST_QR("first argument must be a QR decomposition"),
        ONLY_SQUARE_INVERTED("only square matrices can be inverted"),
        NON_NUMERIC_ARGUMENT_FUNCTION("non-numeric argument to function"),
        SEED_LENGTH(".Random.seed has wrong length"),
        SAME_TYPE("'%s' and '%s' must have the same type"),
        UNIMPLEMENTED_TYPE_IN_FUNCTION("unimplemented type '%s' in '%s'"),
        // below: not exactly GNU-R message
        PROMISE_CYCLE("promise already under evaluation: recursive default argument reference or earlier problems?"),
        MISSING_ARGUMENTS("'missing' can only be used for arguments"),
        INVALID_ENVIRONMENT("invalid environment specified"),
        ENVIR_NOT_LENGTH_ONE("numeric 'envir' arg not of length one"),
        FMT_NOT_CHARACTER("'fmt' is not a character vector"),
        UNSUPPORTED_TYPE("unsupported type"),
        AT_MOST_ONE_ASTERISK("at most one asterisk '*' is supported in each conversion specification"),
        TOO_FEW_ARGUMENTS("too few arguments"),
        ARGUMENT_STAR_NUMBER("argument for '*' conversion specification must be a number"),
        EXACTLY_ONE_WHICH("exactly one attribute 'which' must be given"),
        ATTRIBUTES_NAMED("attributes must be named"),
        MISSING_INVALID("missing value is invalid"),
        TYPE_EXPECTED("%s argument expected"),
        CANNOT_CHANGE_DIRECTORY("cannot change working directory"),
        FIRST_ARG_MUST_BE_STRING("first argument must be a character string"),
        MUST_BE_STRING_OR_FUNCTION("'%s' must be a character string or a function"),
        ARG_MUST_BE_CHARACTER_VECTOR_LENGTH_ONE("argument must be a character vector of length 1"),
        ARG_SHOULD_BE_CHARACTER_VECTOR_LENGTH_ONE("argument should be a character vector of length 1\nall but the first element will be ignored"),
        ZERO_LENGTH_VARIABLE("attempt to use zero-length variable name"),
        ARGUMENT_NOT_INTERPRETABLE_LOGICAL("argument is not interpretable as logical"),
        OPERATIONS_NUMERIC_LOGICAL_COMPLEX("operations are possible only for numeric, logical or complex types"),
        MATCH_VECTOR_ARGS("'match' requires vector arguments"),
        DIMNAMES_NONARRAY("'dimnames' applied to non-array"),
        DIMNAMES_LIST("'dimnames' must be a list"),
        NO_ARRAY_DIMNAMES("no 'dimnames' attribute for array"),
        MISSING_SUBSCRIPT("[[ ]] with missing subscript"),
        IMPROPER_SUBSCRIPT("[[ ]] improper number of subscripts"),
        ROWNAMES_STRING_OR_INT("row names must be 'character' or 'integer', not '%s'"),
        ONLY_FIRST_USED("numerical expression has %d elements: only the first used"),
        NO_SUCH_INDEX("no such index at level %d"),
        LIST_COERCION("(list) object cannot be coerced to type '%s'"),
        CAT_ARGUMENT_LIST("argument %d (type 'list') cannot be handled by 'cat'"),
        DATA_NOT_MULTIPLE_ROWS("data length [%d] is not a sub-multiple or multiple of the number of rows [%d]"),
        ARGUMENT_NOT_MATCH("supplied argument name '%s' does not match '%s'"),
        ARGUMENT_MISSING("argument \"%s\" is missing, with no default"),
        UNKNOWN_FUNCTION("could not find function \"%s\""),
        UNKNOWN_FUNCTION_USE_METHOD("no applicable method for '%s' applied to an object of class '%s'"),
        UNKNOWN_OBJECT("object '%s' not found"),
        INVALID_ARGUMENT("invalid '%s' argument"),
        INVALID_POS_ARGUMENT("invalid 'pos' argument"),
        INVALID_VALUE("invalid '%s' value"),
        INVALID_ARGUMENTS_NO_QUOTE("invalid %s arguments"),
        INVALID_SUBSCRIPT_TYPE("invalid subscript type '%s'"),
        ARGUMENT_NOT_VECTOR("argument %d is not a vector"),
        CANNOT_COERCE("cannot coerce type '%s' to vector of type '%s'"),
        ARGUMENT_ONLY_FIRST("argument '%s' has length > 1 and only the first element will be used"),
        ARGUMENT_ONLY_FIRST_1("only the first element of '%s' argument used"),
        CANNOT_OPEN_FILE("cannot open file '%s': %s"),
        NOT_CONNECTION("'%s' is not a connection"),
        UNUSED_TEXTCONN("closing unused text connection %d (%s)"),
        INCOMPLETE_FINAL_LINE("incomplete final line found on '%s'"),
        CANNOT_OPEN_PIPE("cannot open pipe() cmd '%s': %s"),
        INVALID_TYPE_ARGUMENT("invalid 'type' (%s) of argument"),
        ATTRIBUTE_VECTOR_SAME_LENGTH("'%s' attribute [%d] must be the same length as the vector [%d]"),
        SCAN_UNEXPECTED("scan() expected '%s', got '%s'"),
        MUST_BE_ENVIRON("'%s' must be an environment"),
        UNUSED_ARGUMENT("unused argument (%s)"),
        UNUSED_ARGUMENTS("unused arguments (%s)"),
        INFINITE_MISSING_VALUES("infinite or missing values in '%s'"),
        NON_SQUARE_MATRIX("non-square matrix in '%s'"),
        LAPACK_ERROR("error code %d from Lapack routine '%s'"),
        VALUE_OUT_OF_RANGE("value out of range in '%s'"),
        MUST_BE_STRING("'%s' must be a character string"),
        ARGUMENT_MUST_BE_STRING("argument '%s' must be a character string"),
        ARGUMENT_MUST_BE_RAW_VECTOR("argument '%s' must be a raw vector"),
        MUST_BE_NONNULL_STRING("'%s' must be non-null character string"),
        IS_OF_WRONG_LENGTH("'%s' is of wrong length %d (!= %d)"),
        IS_OF_WRONG_ARITY("'%d' argument passed to '%s' which requires '%d'"),
        OBJECT_NOT_SUBSETTABLE("object of type '%s' is not subsettable"),
        WRONG_ARGS_SUBSET_ENV("wrong arguments for subsetting an environment"),
        DIMS_DONT_MATCH_LENGTH("dims [product %d] do not match the length of object [%d]"),
        DIMNAMES_DONT_MATCH_DIMS("length of 'dimnames' [%d] must match that of 'dims' [%d]"),
        DIMNAMES_DONT_MATCH_EXTENT("length of 'dimnames' [%d] not equal to array extent"),
        MUST_BE_ATOMIC("'%s' must be atomic"),
        MUST_BE_NULL_OR_STRING("'%s' must be NULL or a character vector"),
        IS_NULL("'%s' is NULL"),
        MUST_BE_SCALAR("'%s' must be of length 1"),
        ROWS_MUST_MATCH("number of rows of matrices must match (see arg %d)"),
        ROWS_NOT_MULTIPLE("number of rows of result is not a multiple of vector length (arg %d)"),
        ARG_ONE_OF("'%s' should be one of %s"),
        MUST_BE_SQUARE("'%s' must be a square matrix"),
        NON_MATRIX("non-matrix argument to '%s'"),
        NON_NUMERIC_ARGUMENT_TO("non-numeric argument to '%s'"),
        DIMS_GT_ZERO("'%s' must have dims > 0"),
        NOT_POSITIVE_DEFINITE("the leading minor of order %d is not positive definite"),
        LAPACK_INVALID_VALUE("argument %d of Lapack routine %s had invalid value"),
        RHS_SHOULD_HAVE_ROWS("right-hand side should have %d not %d rows"),
        SAME_NUMBER_ROWS("'%s' and '%s' must have the same number of rows"),
        EXACT_SINGULARITY("exact singularity in '%s'"),
        SINGULAR_SOLVE("singular matrix '%s' in solve"),
        SEED_TYPE(".Random.seed is not an integer vector but of type '%s'"),
        INVALID_USE("invalid use of '%s'"),
        FORMAL_MATCHED_MULTIPLE("formal argument \"%s\" matched by multiple actual arguments"),
        ARGUMENT_MATCHES_MULTIPLE("argument %d matches multiple formal arguments"),
        ARGUMENT_EMPTY("argument %d is empty"),
        REPEATED_FORMAL("repeated formal argument '%s'"),
        NOT_A_MATRIX_UPDATE_CLASS("invalid to set the class to matrix unless the dimension attribute is of length 2 (was %d)"),
        NOT_ARRAY_UPDATE_CLASS("cannot set class to \"array\" unless the dimension attribute has length > 0"),
        SET_INVALID_CLASS_ATTR("attempt to set invalid 'class' attribute"),
        NOT_LEN_ONE_LOGICAL_VECTOR("'%s' must be a length 1 logical vector"),
        TOO_LONG_CLASS_NAME("class name too long in '%s'"),
        NON_STRING_GENERIC("'generic' argument must be a character string"),
        OBJECT_NOT_SPECIFIED("object not specified"),
        NO_METHOD_FOUND("no method to invoke"),
        GEN_FUNCTION_NOT_SPECIFIED("generic function not specified"),
        DUPLICATE_SWITCH_DEFAULT("duplicate 'switch' defaults: '%s' and '%s'"),
        NO_ALTERNATIVE_IN_SWITCH("empty alternative in numeric switch"),
        EXPR_NOT_LENGTH_ONE("EXPR must be a length 1 vector"),
        EXPR_MISSING("'EXPR' is missing"),
        INVALID_STORAGE_MODE_UPDATE("invalid to change the storage mode of a factor"),
        NULL_VALUE("'value' must be non-null character string"),
        USE_DEFUNCT("use of '%s' is defunct: use %s instead"),
        NCOL_ZERO("nc(0 for non-null data"),
        NROW_ZERO("nr(0 for non-null data"),
        SAMPLE_LARGER_THAN_POPULATION("cannot take a sample larger than the population when 'replace(FALSE'\n"),
        SAMPLE_OBJECT_NOT_FOUND("object '%s' not found"),
        ERROR_IN_SAMPLE("Error in sample.int(x, size, replace, prob) :  "),
        INCORRECT_NUM_PROB("incorrect number of probabilities"),
        NA_IN_PROB_VECTOR("NA in probability vector"),
        NEGATIVE_PROBABILITY("non-positive probability"),
        NON_POSITIVE_FILL("non-positive 'fill' argument will be ignored"),
        MUST_BE_ONE_BYTE("invalid %s: must be one byte"),
        INVALID_DECIMAL_SEP("invalid decimal separator"),
        INVALID_QUOTE_SYMBOL("invalid quote symbol set"),
        // below: not exactly GNU-R message
        TOO_FEW_POSITIVE_PROBABILITY("too few positive probabilities"),
        DOTS_BOUNDS("The ... list does not contain %s elements"),
        REFERENCE_NONEXISTENT("reference to non-existent argument %d"),
        UNRECOGNIZED_FORMAT("unrecognized format specification '%s'"),
        INVALID_FORMAT_LOGICAL("invalid format '%s'; use format %%d or %%i for logical objects"),
        INVALID_FORMAT_INTEGER("invalid format '%s'; use format %%d, %%i, %%o, %%x or %%X for integer objects"),
        // the following list is incomplete (but like GNU-R)
        INVALID_FORMAT_DOUBLE("invalid format '%s'; use format %%f, %%e, %%g or %%a for numeric objects"),
        INVALID_LOGICAL("'%s' must be TRUE or FALSE"),
        INVALID_FORMAT_STRING("invalid format '%s'; use format %%s for character objects"),
        MUST_BE_CHARACTER("'%s' must be of mode character"),
        ALL_ATTRIBUTES_NAMES("all attributes must have names [%d does not]"),
        INVALID_REGEXP("invalid regular expression '%s'"),
        COERCING_ARGUMENT("coercing argument of type '%s' to %s"),
        MUST_BE_TRUE_FALSE_ENVIRONMENT("'%s' must be TRUE, FALSE or an environment"),
        UNKNOWN_OBJECT_MODE("object '%s' of mode '%s' was not found"),
        WRONG_LENGTH_ARG("wrong length for '%s' argument"),
        INVALID_TYPE_IN("invalid '%s' type in 'x %s y'"),
        DOT_DOT_MISSING("'..%d' is missing"),
        DOT_DOT_SHORT("the ... list does not contain %d elements"),
        NO_DOT_DOT("..%d used in an incorrect context, no ... to look in"),
        NO_DOT_DOT_DOT("'...' used in an incorrect context"),
        NO_LIST_FOR_CDR("'nthcdr' needs a list to CDR down"),
        INVALID_TYPE_LENGTH("invalid type/length (%s/%d) in vector allocation"),
        SUBASSIGN_TYPE_FIX("incompatible types (from %s to %s) in subassignment type fix"),
        SUBSCRIPT_TYPES("incompatible types (from %s to %s) in [[ assignment"),
        INCOMPATIBLE_METHODS("incompatible methods (\"%s\", \"%s\") for \"%s\""),
        RECURSIVE_INDEXING_FAILED("recursive indexing failed at level %d"),
        ARGUMENTS_PASSED("%d arguments passed to '%s' which requires %d"),
        ARGUMENTS_PASSED_0_1("0 arguments passed to '%s' which requires 1"),
        ARGUMENT_IGNORED("argument '%s' will be ignored"),
        NOT_CHARACTER_VECTOR("'%s' must be a character vector"),
        CANNOT_MAKE_VECTOR_OF_MODE("vector: cannot make a vector of mode '%s'"),
        SET_ROWNAMES_NO_DIMS("attempt to set 'rownames' on an object with no dimensions"),
        COLUMNS_NOT_MULTIPLE("number of columns of result is not a multiple of vector length (arg %d)"),
        DATA_FRAMES_SUBSET_ACCESS("data frames subset access not supported"),
        CANNOT_ASSIGN_IN_EMPTY_ENV("cannot assign values in the empty environment"),
        CANNOT_OPEN_CONNECTION("cannot open the connection"),
        ERROR_READING_CONNECTION("error reading connection: %s"),
        ERROR_WRITING_CONNECTION("error writing connection: %s"),
        ERROR_FLUSHING_CONNECTION("error flushing connection: %s"),
        ALREADY_OPEN_CONNECTION("connection is already open"),
        NO_ITEM_NAMED("no item named '%s' on the search list"),
        INVALID_OBJECT("invalid object for 'as.environment'"),
        EMPTY_NO_PARENT("the empty environment has no parent"),
        ARG_NOT_AN_ENVIRONMENT("argument to '%s' is not an environment"),
        NOT_AN_ENVIRONMENT("not an environment"),
        NOT_A_SYMBOL("not a symbol"),
        CANNOT_SET_PARENT("cannot set the parent of the empty environment"),
        INVALID_OR_UNIMPLEMENTED_ARGUMENTS("invalid or unimplemented arguments"),
        NOTHING_TO_LINK("nothing to link"),
        FROM_TO_DIFFERENT("'from' and 'to' are of different lengths"),
        NA_IN_FOREIGN_FUNCTION_CALL("NAs in foreign function call (arg %d)"),
        NA_NAN_INF_IN_FOREIGN_FUNCTION_CALL("NA/NaN/Inf in foreign function call (arg %d)"),
        INCORRECT_ARG("incorrect arguments to %s"),
        UNIMPLEMENTED_ARG_TYPE("unimplemented argument type (arg %d)"),
        C_SYMBOL_NOT_IN_TABLE("C symbol name \"%s\" not in load table"),
        FORTRAN_SYMBOL_NOT_IN_TABLE("Fortran symbol name \"%s\" not in load table"),
        NOT_THAT_MANY_FRAMES("not that many frames on the stack"),
        UNIMPLEMENTED_ARGUMENT_TYPE("unimplemented argument type"),
        MUST_BE_SQUARE_NUMERIC("'%s' must be a square numeric matrix"),
        MUST_BE_NUMERIC_MATRIX("'%s' must be a numeric matrix"),
        PARSE_ERROR("parse error"),
        SEED_NOT_VALID_INT("supplied seed is not a valid integer"),
        POSITIVE_CONTEXTS("number of contexts must be positive"),
        INVALID_TIMES_ARG("invalid 'times' value"),
        NORMALIZE_PATH_NOSUCH("path[%d]=\"%s\": No such file or directory"),
        ARGS_MUST_BE_NAMED("all arguments must be named"),
        INVALID_INTERNAL("invalid .Internal() argument"),
        NO_SUCH_INTERNAL("there is no .Internal function '%s'"),
        NO_SUCH_PRIMITIVE("no such primitive function"),
        INVALID_VALUE_FOR("invalid value for '%s'"),
        IMP_EXP_NAMES_MATCH("length of import and export names must match"),
        ENV_ADD_BINDINGS("cannot add bindings to a locked environment"),
        ENV_REMOVE_BINDINGS("cannot remove bindings from a locked environment"),
        ENV_REMOVE_VARIABLES("cannot remove variables from the %s environment"),
        ENV_CHANGE_BINDING("cannot change value of locked binding for '%s'"),
        ENV_ASSIGN_EMPTY("cannot assign values in the empty environment"),
        ENV_DETACH_BASE("detaching \"package:base\" is not allowed"),
        ENV_SUBSCRIPT("subscript out of range"),
        DLL_LOAD_ERROR("unable to load shared object '%s'\n  %s"),
        DLL_NOT_LOADED("shared object '%s' was not loaded"),
        DLL_RINIT_ERROR("package 'init' method failed"),
        RNG_BAD_KIND("RNG kind %s is not available"),
        RNG_NOT_IMPL_KIND("unimplemented RNG kind %d"),
        RNG_READ_SEEDS("cannot read seeds unless 'user_unif_nseed' is supplied"),
        RNG_SYMBOL("%s not found in user rng library"),
        CUMMAX_UNDEFINED_FOR_COMPLEX("'cummin' not defined for complex numbers"),
        CUMMIN_UNDEFINED_FOR_COMPLEX("'cummax' not defined for complex numbers"),
        NMAX_LESS_THAN_ONE("'nmax' must be positive"),
        CHAR_VEC_ARGUMENT("a character vector argument expected"),
        QUOTE_G_ONE("only the first character of 'quote' will be used"),
        UNEXPECTED("unexpected '%s' in \"%s\""),
        UNEXPECTED_LINE("unexpected '%s' in \"%s\" (line %d)"),
        FIRST_ELEMENT_USED("first element used of '%s' argument"),
        MUST_BE_COERCIBLE_INTEGER("argument must be coercible to non-negative integer"),
        DEFAULT_METHOD_NOT_IMPLEMENTED_FOR_TYPE("default method not implemented for type '%s'"),
        ARG_MUST_BE_CLOSURE("argument must be a closure"),
        NOT_DEBUGGED("argument is not being debugged"),
        ADDING_INVALID_CLASS("adding class \"%s\" to an invalid object"),
        IS_NA_TO_NON_VECTOR("is.na() applied to non-(list or vector) of type '%s'"),
        NOT_MEANINGFUL_FOR_FACTORS("\u2018%s\u2019 not meaningful for factors"),
        INPUTS_DIFFERENT_LENGTHS("inputs of different lengths"),
        MATRIX_LIKE_REQUIRED("a matrix-like object is required as argument to '%s'"),
        NOT_MEANINGFUL_FOR_ORDERED_FACTORS("'%s' is not meaningful for ordered factors"),
        UNSUPPORTED_URL_SCHEME("unsupported URL scheme"),
        CANNOT_CLOSE_STANDARD_CONNECTIONS("cannot close standard connections"),
        FULL_PRECISION("full precision may not have been achieved in '%s'"),
        ATTACH_BAD_TYPE("'attach' only works for lists, data frames and environments"),
        STRING_ARGUMENT_REQUIRED("string argument required"),
        FILE_APPEND_TO("nothing to append to"),
        FILE_OPEN_TMP("file(\"\") only supports open = \"w+\" and open = \"w+b\": using the former"),
        FILE_APPEND_WRITE("write error during file append"),
        REQUIRES_CHAR_VECTOR("'%s' requires a character vector"),
        NOT_VALID_NAMES("not a valid named list"),
        CHAR_ARGUMENT("character argument expected"),
        CANNOT_BE_INVALID("'%s' cannot be NA, NaN or infinite"),
        UNKNOWN_VALUE("unknown '%s' value"),
        MUST_BE_VECTOR("'%s' must be a vector"),
        NO_SUCH_CONNECTION("there is no connection %d"),
        REQUIRES_DLLINFO("R_getRegisteredRoutines() expects a DllInfo reference"),
        NULL_DLLINFO("NULL value passed for DllInfo"),
        REQUIRES_NAME_DLLINFO("must pass package name or DllInfo reference"),
        APPLY_NON_FUNCTION("attempt to apply non-function"),
        NO_INDEX("no index specified"),
        INVALID_ARG_NUMBER("%s: invalid number of arguments"),
        BAD_HANDLER_DATA("bad handler data"),
        DEPARSE_INVALID_CUTOFF("invalid 'cutoff' value for 'deparse', using default"),
        FILE_CANNOT_CREATE("cannot create file '%s'"),
        FILE_CANNOT_LINK("  cannot link '%s' to '%s', reason %s"),
        FILE_CANNOT_COPY("  cannot link '%s' to '%s', reason %s"),
        FILE_CANNOT_REMOVE("  cannot remove file '%s'"),
        FILE_CANNOT_RENAME("  cannot rename file '%s' to '%s'"),
        FILE_COPY_RECURSIVE_IGNORED("'recursive' will be ignored as 'to' is not a single existing directory"),
        FILE_OPEN_ERROR("unable to open file"),
        DIR_CANNOT_CREATE("cannot create dir '%s'"),
        IMPOSSIBLE_SUBSTITUTE("substitute result cannot be represented"),
        PACKAGE_AVAILABLE("'%s' may not be available when loading"),
        BAD_RESTART("bad restart"),
        RESTART_NOT_ON_STACK("restart not on stack"),
        PERFORMANCE("performance problem: %s"),
        MUST_BE_SMALL_INT("argument '%s' must be a small integer"),
        NO_INTEROP("'%s' is not an object that supports interoperability (class %s)"),
        NO_IMPORT_OBJECT("'%s' is not an exported object"),
        NO_FUNCTION_RETURN("no function to return from, jumping to top level"),
        REG_FINALIZER_FIRST("first argument must be environment or external pointer"),
        REG_FINALIZER_SECOND("second argument must be a function"),
        REG_FINALIZER_THIRD("third argument must be 'TRUE' or 'FALSE'"),
        LAZY_LOAD_DB_CORRUPT("lazy-load database '%s' is corrupt"),
        MAGIC_EMPTY("restore file may be empty -- no data loaded"),
        MAGIC_TOONEW("restore file may be from a newer version of R -- no data loaded"),
        MAGIC_CORRUPT("bad restore file magic number (file may be corrupted) -- no data loaded");

        public final String message;
        final boolean hasArgs;

        private Message(String message) {
            this.message = message;
            hasArgs = message.indexOf('%') >= 0;
        }
    }
}
