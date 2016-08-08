
## AST-X Compiler
## EULER Benchmark

## Parse arguments
args <- commandArgs(trailingOnly=TRUE)

if (length(args) == 0) {
	stop("No input size passed. Usage: ./euler.R <size> ")
} 

size <- as.integer(args[1])

REPETITIONS <- 10

benchmark <- function(inputSize) {

	eulerFunction <- function(x) {

		e <- x
		result <- list(0,0,0,0)
		for (a in 1:size) {
			a5 <- input[a]
			for (b in a:size) {
				b5 <- input[b]
				for (c in b:size) {
					c5 <- input[c]
					sum <- a5 + b5 + c5
					if (sum == e) {
						result[[1]] = a5
						result[[2]] = b5
						result[[3]] = c5
						result[[4]] = e
					}			
				}
			}
		}
		return(result)
	}	

	x <- 1:size;
		
	input <<- runif(size)

	for (i in 1:REPETITIONS) {
		start <- nanotime()
		result <- mapply(eulerFunction, x);
		total <- nanotime() - start
		print(total)
	}
}

## Main 
print("FASTR CPU")
print(paste("SIZE:", size))
benchmark(size)

