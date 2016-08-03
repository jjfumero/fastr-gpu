
## ASTx
## Montecarlo benchmark, baseline 

## Parse arguments
args <- commandArgs(trailingOnly=TRUE)

if (length(args) == 0) {
	stop("No input size passed. Usage: ./montecarlo.R <size> ")
} 

size <- as.integer(args[1])

REPETITIONS <- 10

## Lambda expression for the computation
benchmark <- function(inputSize) {

	montecarloFunction <- function(x) {
		iter <- 25000

		seed <- x
		sum <- 0.0
		
		for (j in 1:iter) {
			x <- runif(1)
			y <- runif(1)
			dist <- sqrt(x*x + y * y)
			if (dist <= 1.0) {
				sum <- sum + 1.0;
			}
		}
	}

	x <- 0:size;

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

