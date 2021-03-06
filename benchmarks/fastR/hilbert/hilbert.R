
## AST-X Compiler
## Hilbert benchmark, baseline 

## Parse arguments
args <- commandArgs(trailingOnly=TRUE)

if (length(args) == 0) {
	stop("No input size passed. Usage: ./hilbert.R <size> ")
} 

size <- as.integer(args[1])

REPETITIONS <-11

CHECK_RESULT <- FALSE

## Lambda expression for the computation
benchmark <- function(inputSize) {

	hilbertFunction <- function(x, y) {
		value <- 1 / (x + y - 1)
		result <- (value)
	}	

	totalSize <- size*size
	x <- flagSequence(1, size, size)
	y <- compassSequence(1, size, size)

	for (i in 1:REPETITIONS) {
		start <- nanotime()
		result <- mapply(hilbertFunction, x, y);
		total <- nanotime() - start
		print(paste("Total Time: ", total))
	}
}

## Main 
print("FASTR CPU")
print(paste("SIZE:", size))
benchmark(size)

