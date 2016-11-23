
## AST-X Compiler
## Hilbert benchmark 

args <- commandArgs(trailingOnly=TRUE)

if (length(args) == 0) {
	stop("No input size passed. Usage: ./hilbertGNU.R <size> ")
} 

size <- as.integer(args[1])

REPETITIONS <- 11

CHECK_RESULT <- FALSE

require(compiler)
enableJIT(3)

benchmark <- function(inputSize) {

	hilbertFunction <- function(x, y) {
		value <- 1 / (x + y - 1)
		result <- (value)
	}	

	totalSize <- size*size
	x <- 1:totalSize
	y <- 1:totalSize

	for (i in 1:REPETITIONS) {
		start <- proc.time()
		result <- mapply(hilbertFunction, x, y);
		total <- proc.time() - start
		print(total)
	}
}

## Main 
print("FASTR CPU")
print(paste("SIZE:", size))
benchmark(size)

