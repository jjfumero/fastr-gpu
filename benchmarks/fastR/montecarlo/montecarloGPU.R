
## ASTx
## Montecarlo benchmark

## Parse arguments
args <- commandArgs(trailingOnly=TRUE)

if (length(args) == 0) {
	stop("No input size passed. Usage: ./montecarloGPU.R <size> ")
} 

size <- as.integer(args[1])

REPETITIONS <-11

CHECK_RESULT <- FALSE

## Lambda expression for the computation
benchmark <- function(inputSize) {

	montecarloGPUFunction <- function(idx) {
		iterations <- 25000

		sum <- 0.0
		
		x <- rx[idx] 
		y <- ry[idx] 

		d <- x * x + y * y			
		dist <- sqrt(d)
			
		if (dist <= 1.0) {
			sum <- sum + 1.0;
		} 

		return(sum)		
	}

	x <- 1:size;
	rx <<- runif(size)
	ry <<- runif(size)

	for (i in 1:REPETITIONS) {
		start <- nanotime()
		result <- marawacc.gpusapply(x, montecarloGPUFunction);
		end <- nanotime()
		total <- end - start
		print(paste("Total Time: ",total))

		if (CHECK_RESULT) {
			for (i in 1:size) {
				if (abs(resultSeq[i] - result[i]) > 0.1) {
					print("Result is wrong")
					break
				}
			}
		}
	}
}

## Main 
print("ASTx GPU")
print(paste("SIZE:", size))
benchmark(size)

