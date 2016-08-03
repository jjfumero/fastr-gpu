
## ASTx
## Montecarlo benchmark, baseline 

## Parse arguments
args <- commandArgs(trailingOnly=TRUE)

if (length(args) == 0) {
	stop("No input size passed. Usage: ./montecarloGPU.R <size> ")
} 

size <- as.integer(args[1])

REPETITIONS <- 10

## Lambda expression for the computation
benchmark <- function(inputSize) {

	montecarloGPUFunction <- function(x) {
		iter <- 25000

		seed <- x
		sum <- 0.0
		
		for (j in 1:iter) {
			# random for x
			#x <- runif(1)
			# random for y
			#y <- runif(1)

			# temporal
			x <- 0.1232
			y <- 0.8712

			dist <- sqrt(x*x + y * y)
			if (dist <= 1.0) {
				sum <- sum + 1.0;
			}
		}
		
		return(sum)
	}

	montecarloCPUFunction <- function(x) {
		iter <- 25000

		seed <- x
		sum <- 0.0
		
		for (j in 1:iter) {
			# random for x
			#x <- runif(1)
			# random for y
			#y <- runif(1)

			# temporal
			x <- 0.1232
			y <- 0.8712

			dist <- sqrt(x*x + y * y)
			if (dist <= 1.0) {
				sum <- sum + 1.0;
			}
		}
		return(sum)
	}

	x <- 0:size;

	resultSeq <- mapply(montecarloCPUFunction, x)

	for (i in 1:REPETITIONS) {
		start <- nanotime()
		result <- marawacc.gpusapply(x, montecarloGPUFunction);
		end <- nanotime()
		total <- end - start
		print(total)

		# Check result
		for (i in 1:size) {
			if (abs(resultSeq[i] - result[i]) > 0.1) {
				print("Result is wrong")
				break
			}
		}

		#print(result);
	}
}

## Main 
print("ASTx GPU")
print(paste("SIZE:", size))
benchmark(size)

