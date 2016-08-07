
## AST-X Compiler
## DFT Benchmark

## Parse arguments
args <- commandArgs(trailingOnly=TRUE)

if (length(args) == 0) {
	stop("No input size passed. Usage: ./dft.R <size> ")
} 

size <- as.integer(args[1])

REPETITIONS <- 10

benchmark <- function(inputSize) {

	dftFunction <- function(x) {
	
		n <- size
        sumreal <- 0
        sumimag <- 0
		for (t in 1:n) {
        	angle <- 2 * pi * t * x / n;
            sumreal <- sumreal + (inreal[t] * cos(angle) + inimag[t] * sin(angle))
            sumimag <- sumimag + (-inreal[t] * sin(angle) + inimag[t] * cos(angle))
        }

		l <- list(sumreal, sumimag)
		return(l)
	}	

	x <- 0:size;
	inreal <<- runif(1:size)
	inimag <<- runif(1:size)

	for (i in 1:REPETITIONS) {
		start <- nanotime()
		result <- mapply(dftFunction, x);
		total <- nanotime() - start
		print(total)
	}
}

## Main 
print("FASTR CPU")
print(paste("SIZE:", size))
benchmark(size)

