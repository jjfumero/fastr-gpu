## AST-X Compiler
## Matrix Vector multiplication

## Parse arguments
args <- commandArgs(trailingOnly=TRUE)

if (length(args) == 0) {
	stop("No input size passed. Usage: ./matrixVector.R <size> ")
} 

size <- as.integer(args[1])

REPETITIONS <- 1
CHECK_RESULT <- FALSE

## Lambda expression for the computation
benchmark <- function(inputSize) {

	matrixVectorMultiplicationGPU <- function(idx) {
		result <- 0.0
		for (jdx in 1:size) {
			m <- _matrix[idx * size + jdx]
			v <- _vector[jdx]
			r <- m * v
			result <- result + r
		}
		return(result)
	}
	
	matrixVectorMultiplicationCPU <- function(idx) {
		result <- 0.0
		for (jdx in 1:size) {
			result <- result + _matrix[idx * size + jdx] * _vector[jdx]
		}
		return(result)
	}	

	index <- 1:size
	_vector <<- runif(size)	
	_matrix <<- runif(size * size)	

	if (CHECK_RESULT) {
		resultSeq <- mapply(matrixVectorMultiplicationCPU, index)
	}

	for (i in 1:REPETITIONS) {
		system.gc()
		start <- nanotime()
		result <- marawacc.testGPU(index, matrixVectorMultiplicationGPU)
		total <- nanotime() - start
		print(paste("Total Time: ", total))

		if (CHECK_RESULT) {
			for (j in 1:size) {
				if (abs(resultSeq[j] - result[j] > 0.1)) {
					print("Result is wrong")
					break
				}
			}
		}

	}
}

## Main 
print("FASTR CPU")
print(paste("SIZE:", size))
benchmark(size)

