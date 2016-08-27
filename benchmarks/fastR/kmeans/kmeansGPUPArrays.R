
## ASTx
## KMeans benchmark

args <- commandArgs(trailingOnly=TRUE)

if (length(args) == 0) {
	stop("No input size passed. Usage: ./kmeansGPUPArrays.R <size> ")
} 

size <- as.integer(args[1])

REPETITIONS <- 11
KS <- 10

CHECK_RESULT <- TRUE

benchmark <- function(inputSize) {

	kmeansOpenCLFunction <- function(x, y) {
		minDist <- 10000000
		id <- 0
		for (i in 1:KS) {
			currentDist <- (x - centre[i]) * (x - centre[i])  +
						   (y - centre[i + KS]) * (y - centre[i + KS])
			if (currentDist < minDist) {
				minDist <- currentDist
				id <- i
			} else {
			}	
		}
		return(id)
	}

	kmeansFastRFunction <- function(x, y) {
		minDist <- 10000000
		id <- -1
		for (i in 1:KS) {
			currentDist <- (x - centre[i]) * (x - centre[i]) + 
						   (y - centre[i + KS]) * (y - centre[i + KS])
			if (currentDist < minDist) {
				minDist <- currentDist
				id <- i
			}			
		}
		return(id)
	}

	centre <<- runif(KS*2)  * 10 * 2 - 10 
	x <- runif(size)
	y <- runif(size)

	if (CHECK_RESULT) {
		resultSeq <- mapply(kmeansFastRFunction, x, y);
	}

	x <- marawacc.parray(x)	
	y <- marawacc.parray(y)	

	for (i in 1:REPETITIONS) {
		start <- nanotime()
		result <- marawacc.gpusapply(x, kmeansOpenCLFunction, y);
		end <- nanotime()
		total <- end - start
		print(paste("Total Time: ", total))

		if (CHECK_RESULT) {
			nonError <- identical(resultSeq, result)
			correct <- TRUE
			if (!nonError) {
				for (i in seq(result)) {
					if (abs(resultSeq[i] - result[i]) > 0.1) {
						print("Result is wrong")
						correct <- FALSE
						break;
					}
				}
			}
		}
	}
}

## Main
print("ASTx GPU")
print(paste("SIZE:", size))
benchmark(size)

