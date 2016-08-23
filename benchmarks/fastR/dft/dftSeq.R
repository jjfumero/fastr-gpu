
## AST-X Compiler
## DFT Benchmark

## Parse arguments
args <- commandArgs(trailingOnly=TRUE)

if (length(args) == 0) {
	stop("No input size passed. Usage: ./dftSeq.R <size> ")
} 

size <- as.integer(args[1])

REPETITIONS <- 11

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

	x <- 1:size;
	inreal <<- runif(size)
	inimag <<- runif(size)

	for (i in 1:REPETITIONS) {
		start <- nanotime()
		result <- marawacc.sapply(x, dftFunction);
		total <- nanotime() - start
		print(paste("Total Time:" , total))
	}
}

## Main 
print("ASTx sequential")
print(paste("SIZE:", size))
benchmark(size)

