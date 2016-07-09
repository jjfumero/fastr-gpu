
## AST-X Compiler
## Saxpy benchmark, GPU version

## Parse arguments
args <- commandArgs(trailingOnly=TRUE)

if (length(args) == 0) {
	stop("No input size passed. Usage: ./saxpyGPU.R <size> ")
} 

size <- as.integer(args[1])

REPETITIONS <- 100

## Lambda expression for the computation
benchmark <- function(inputSize) {

	saxpyFunction <- function(x, y) {
		result <- (0.12 * x) + y;
		return (result);
	}	

	x <- 0:size;
	y <- 0:size;

	for (i in 1:REPETITIONS) {
		start <- nanotime()
		result <- marawacc.testGPU(x, saxpyFunction, y);
		total <- nanotime() - start
		print(total)
		#print(result);
	}
}

## Main 
benchmark(size)

