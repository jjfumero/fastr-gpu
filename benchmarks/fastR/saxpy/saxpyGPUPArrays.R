
## AST-X Compiler
## Saxpy benchmark, GPU version

## Parse arguments
args <- commandArgs(trailingOnly=TRUE)

if (length(args) == 0) {
	stop("No input size passed. Usage: ./saxpyGPUPArrays.R <size> ")
} 

size <- as.integer(args[1])

REPETITIONS <- 1

CHECK <- FALSE

## Lambda expression for the computation
benchmark <- function(inputSize) {

	saxpyFunction <- function(x, y) {
		result <- (0.12 * x) + y;
		return (result);
	}	

	x <- runif(size)
	y <- runif(size)

	if (CHECK) {
		resultSeq <- 0.12 * x + y
	}

	x <- marawacc.parray(x)
	y <- marawacc.parray(y)

	for (i in 1:REPETITIONS) {
		start <- nanotime()
		result <- marawacc.gpusapply(x, saxpyFunction, y);
		total <- nanotime() - start
		print(paste("Total Time: ", total))

		# check result
		if (CHECK) {
			nonError <- identical(resultSeq, result)
			correct <- TRUE
			if (!nonError) {
				for (i in seq(result)) {
					if (abs(resultSeq[i] - result[i]) > 0.5) {
						print(nonError)
						print(i)
						print(resultSeq[i])
						print(result[i])
						correct <- FALSE
						break;
					}
				}		
			} 
			#print(result);
		}
	}
}

## Main 
print("ASTx GPU PARRAY")
print(paste("SIZE:", size))
benchmark(size)

