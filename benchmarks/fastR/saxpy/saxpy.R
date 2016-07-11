
## AST-X Compiler
## Saxpy benchmark, baseline 

## Parse arguments
args <- commandArgs(trailingOnly=TRUE)

if (length(args) == 0) {
	stop("No input size passed. Usage: ./saxpy.R <size> ")
} 

size <- as.integer(args[1])

REPETITIONS <- 100

## Lambda expression for the computation
benchmark <- function(inputSize) {

	saxpyFunction <- function(x, y) {
		result <- (alpha * x) + y;
		return (result);
	}	

	alpha <- 0.12

	x <- 0:size;
	y <- 0:size;

	for (i in 1:REPETITIONS) {
		start <- nanotime()
		result <- mapply(saxpyFunction, x, y);
		total <- nanotime() - start
		print(total)
		#print(result);
	}
}

## Main 
benchmark(size)

