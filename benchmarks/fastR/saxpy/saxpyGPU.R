
## AST-X Compiler
## Saxpy benchmark, GPU version

## Parse arguments
args <- commandArgs(trailingOnly=TRUE)

if (length(args) == 0) {
	stop("No input size passed. Usage: ./saxpyGPU.R <size> ")
} 

size <- as.integer(args[1])

REPETITIONS <-11

## Lambda expression for the computation
benchmark <- function(inputSize) {

	saxpyFunction <- function(x, y) {
		result <- (0.12 * x) + y;
		return (result);
	}	

	x <- runif(size)
	y <- runif(size)
	
	# Seq code
	resultSeq <- 0.12 * x + y

	for (i in 1:REPETITIONS) {
		system.gc()
		start <- nanotime()
		result <- marawacc.gpusapply(x, saxpyFunction, y);
		total <- nanotime() - start
		print(paste("Total Time: ", total))

		# check result
		nonError <- identical(resultSeq, result)
		correct <- TRUE
		if (!nonError) {
			for (i in seq(result)) {
				if (abs(resultSeq[i] - result[i]) > 0.1) {
					print(nonError)
					correct <- FALSE
					break;
				}
			}
			if (correct) {
				#print("Result is correct")
			}
				
		} else {
			#print("Result is correct")
		}
	}
}

## Main 
print("ASTx GPU")
print(paste("SIZE:", size))
benchmark(size)

