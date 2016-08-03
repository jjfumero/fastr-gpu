
## AST-X Compiler
## Saxpy benchmark, baseline 

## Parse arguments
args <- commandArgs(trailingOnly=TRUE)

if (length(args) == 0) {
	stop("No input size passed. Usage: ./saxpy.R <size> ")
} 

size <- as.integer(args[1])

REPETITIONS <- 10

## Lambda expression for the computation
benchmark <- function(inputSize) {

	saxpyFunction <- function(x, y) {
		result <- (alpha * x) + y;
		return (result);
	}	

	alpha <- 0.12

	x <- 0:size;
	y <- 0:size;

	# Seq code
	resultSeq <- alpha * x + y
	correct <- TRUE

	for (i in 1:REPETITIONS) {
		start <- nanotime()
		result <- mapply(saxpyFunction, x, y);
		total <- nanotime() - start
		print(total)

		# check result
		nonError <- identical(resultSeq, result)
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
print("FASTR CPU")
print(paste("SIZE:", size))
benchmark(size)

