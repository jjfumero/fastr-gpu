
## AST-X Compiler
## Saxpy benchmark, GPU version

## Parse arguments
args <- commandArgs(trailingOnly=TRUE)

if (length(args) == 0) {
	stop("No input size passed. Usage: ./deopt01.R <size> ")
} 

size <- as.integer(args[1])

REPETITIONS <- 11

## Lambda expression for the computation
benchmark <- function(inputSize) {

	deoptSample <- function(x) {
		if (x == 1) {
			result <- 0
		} else {
			result <- 1
		}
		return (result);
	}	

	x <- rep(1, size)
	x[1000] <- 100

	r <- rep(0, size)
	r[1000] = 1

	for (i in 1:REPETITIONS) {
		start <- nanotime()
		result <- marawacc.gpusapply(x, deoptSample);
		total <- nanotime() - start
		print(paste("Total Time: ", total))

		if (identical(result, r)) {
			print("RESULT IS CORRECT")
		}
	}
}

## Main 
print("ASTx GPU")
print(paste("SIZE:", size))
benchmark(size)

