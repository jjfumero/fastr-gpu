
## AST-X Compiler
## Hilbert benchmark 

args <- commandArgs(trailingOnly=TRUE)

if (length(args) == 0) {
	stop("No input size passed. Usage: ./hilbertGPU.R <size> ")
} 

size <- as.integer(args[1])

REPETITIONS <- 11
CHECK_RESULT <- FALSE

benchmark <- function(inputSize) {

	hilbertFunction <- function(x, y) {
		aux <- x * y + 1
		value <- 1.0 / aux
		return(value)
	}	

	totalSize <- size*size
	x <- 1:totalSize
	y <- 1:totalSize

	for (i in 1:REPETITIONS) {
		start <- nanotime()
		result <- marawacc.testGPU(x, hilbertFunction, y)
		total <- nanotime() - start
		print(paste("Total Time: ", total))
	}
}

## Main 
print("FASTR CPU")
print(paste("SIZE:", size))
benchmark(size)

