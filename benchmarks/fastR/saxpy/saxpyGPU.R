
## AST-X Compiler
## Saxpy benchmark, GPU version

## Parse arguments
args <- commandArgs(trailingOnly=TRUE)

if (length(args) == 0) {
	stop("No input size passed. Usage: ./saxpyGPU.R <size> ")
} 

size <- as.integer(args[1])

REPETITIONS <- 100

## Lambda expression for the computation
benchmark <- function(inputSize) {

	saxpyFunction <- function(x, y) {
		result <- (0.12 * x) + y;
		return (result);
	}	

	x <- 0:size;
	y <- 0:size;

	# Seq code
	resultSeq <- 0.12 * x + y

	for (i in 1:REPETITIONS) {
		start <- nanotime()
		result <- marawacc.testGPU(x, saxpyFunction, y);
		total <- nanotime() - start
		print(total)

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
				print("Result is correct")
			}
				
		} else {
			print("Result is correct")
		}
		#print(result);
	}
}

## Main 
benchmark(size)
