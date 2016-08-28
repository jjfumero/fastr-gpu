
## AST-X Compiler
## Hilbert benchmark 

## Parse arguments
args <- commandArgs(trailingOnly=TRUE)

if (length(args) == 0) {
	stop("No input size passed. Usage: ./hilbertSeq.R <size> ")
} 

size <- as.integer(args[1])

REPETITIONS <- 11

CHECK_RESULT <- FALSE

## Lambda expression for the computation
benchmark <- function(inputSize) {

	hilbertFunction <- function(x, y) {
		value <- 1 / (x + y - 1)
		result <- (value)
	}	

	totalSize <- size*size
	x <- 1:totalSize
	y <- 1:totalSize

	for (i in 1:REPETITIONS) {
		start <- nanotime()
		result <- marawacc.sapply(x, hilbertFunction, nThreads=1, y);
		total <- nanotime() - start
		print(paste("Total Time: ", total))
	}
}

## Main 
print("ASTx Sequential")
print(paste("SIZE:", size))
benchmark(size)

