
## AST-X Compiler
## Matrix Vector multiplication

## Parse arguments
args <- commandArgs(trailingOnly=TRUE)

if (length(args) == 0) {
	stop("No input size passed. Usage: ./matrixVector.R <size> ")
} 

size <- as.integer(args[1])

REPETITIONS <-11

CHECK_RESULT <- FALSE

## Lambda expression for the computation
benchmark <- function(inputSize) {

	matrixVectorMultiplication <- function(idx) {

		result <- 0.0
		for (jdx in 1:size) {
			result <- result + _matrix[idx * size + jdx] * _vector[jdx]
		}
		return(result)
	}	

	index <- 1:size
	_vector <<- runif(size)	
	_matrix <<- runif(size*size)	

	for (i in 1:REPETITIONS) {
		start <- nanotime()
		result <- mapply(matrixVectorMultiplication, index)
		total <- nanotime() - start
		print(paste("Total Time: ", total))
	}
}

## Main 
print("FASTR CPU")
print(paste("SIZE:", size))
benchmark(size)

