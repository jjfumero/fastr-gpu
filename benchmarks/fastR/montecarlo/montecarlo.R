
## FastR Version
## Montecarlo benchmark baseline

## Parse arguments
args <- commandArgs(trailingOnly=TRUE)

if (length(args) == 0) {
	stop("No input size passed. Usage: ./montecarlo.R <size> ")
} 

size <- as.integer(args[1])

REPETITIONS <- 11

## Lambda expression for the computation
benchmark <- function(inputSize) {

	montecarloFunction <- function(input) {
		iter <- 25000
		seed <- input
		sum <- 0.0
		for (j in 1:iter) {
			x <- rx[input]
			y <- ry[input] 
			dist <- sqrt(x*x + y * y)
			if (dist <= 1.0) {
				sum <- sum + 1.0;
			}
		}
		sum <- sum * 4
		result <- sum/iter;
		return(result)
	}

	x <- 1:size;
	rx <<- runif(size)
	ry <<- runif(size)

	for (i in 1:REPETITIONS) {
		start <- nanotime()
		result <- mapply(montecarloFunction, x);
		end <- nanotime()
		total <- end - start
		print(total)
		#print(result);
	}
}

## Main
print("FASTR CPU")
print(paste("SIZE:", size))
benchmark(size)

