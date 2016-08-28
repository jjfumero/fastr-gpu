
## ASTx
## Montecarlo benchmark

## Parse arguments
args <- commandArgs(trailingOnly=TRUE)

if (length(args) == 0) {
	stop("No input size passed. Usage: ./montecarloGPUPArrays.R <size> ")
} 

size <- as.integer(args[1])

REPETITIONS <- 11

CHECK_RESULT <- TRUE

## Lambda expression for the computation
benchmark <- function(inputSize) {

	montecarloGPUFunction <- function(input) {

		iterations <- 25000
		sum <- 0.0
		
		for (i in 1:iterations) {
			x <- rx[input] 
			y <- ry[input] 

			d <- x * x + y * y			
			dist <- sqrt(d)
			
			## The limit in just the first iteration is 0.5
			## to create the explicit branch
			limit <- 1.0
			if (i == 1) {
				limit <- 0.5
			}
		
			if (dist <= limit) {
				sum <- sum + 1.0;
			} 
		}

		sum <- sum * 4
		result <- sum/iterations
		return(result)
	}

	montecarloCPUFunction <- function(input) {
		iterations <- 25000
		sum <- 0.0
		for (i in 1:iterations) {
			x <- rx[input] 
			y <- ry[input] 
			dist <- sqrt(x*x + y * y)
			if (dist <= 1.0) {
				sum <- sum + 1.0;
			}
		}
		sum <- sum * 4
		result <- sum/iterations
		return(result)
	}

	x <- 1:size
	rx <<- runif(size)
	ry <<- runif(size)
	
	if (CHECK_RESULT) {
		resultSeq <- mapply(montecarloCPUFunction, x)
	}

	x <- marawacc.parray(x)

	for (i in 1:REPETITIONS) {
		start <- nanotime()
		result <- marawacc.gpusapply(x, montecarloGPUFunction);
		end <- nanotime()
		total <- end - start
		print(paste("Total Time: ",total))

		if (CHECK_RESULT) {
			nonError <- identical(resultSeq, result)
			if (!nonError) {
				for (j in 1:size) {
					if (abs(resultSeq[j] - result[j]) > 0.1) {
						print("Result is wrong")
						break
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

