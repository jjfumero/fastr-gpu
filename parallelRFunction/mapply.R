mapply <- function (FUN, ..., MoreArgs = NULL, SIMPLIFY = TRUE, USE.NAMES = TRUE) 
{
    FUN <- match.fun(FUN)
    dots <- list(...)
	# OpenCL builtin exploration
	marawacc.init()
	# Return TRUE if there is an OpenCL device available
	isOCL <- marawacc.isOpenCL()
	if (isOCL[1] == 1) {
		# In that case, we switch to the FastR + OpenCL implementation of 
		# mapply builtin 
		dosts <- list(...)
		size <- length(dots)
		if (size > 1) {
			subarray2 <- dots[2:size]
			return(marawacc.testGPU(dots[[1]], FUN, subarray2))
		} else {
			return(marawacc.testGPU(dots[[1]], FUN))
  		}
	}	

    answer <- .Internal(mapply(FUN, dots, MoreArgs))
    if (USE.NAMES && length(dots))
        {
            if (is.null(names1 <- names(dots[[1L]])) && is.character(dots[[1L]]))
                names(answer) <- dots[[1L]]
            else if (!is.null(names1))
                names(answer) <- names1
        }
    if (!identical(SIMPLIFY, FALSE) && length(answer))
        simplify2array(answer, higher = (SIMPLIFY == "array"))
    else answer
}

