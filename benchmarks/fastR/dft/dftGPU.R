
## AST-X Compiler
## DFT Benchmark

## Parse arguments
args <- commandArgs(trailingOnly=TRUE)

if (length(args) == 0) {
	stop("No input size passed. Usage: ./dftGPU.R <size> ")
} 

size <- as.integer(args[1])

REPETITIONS <- 10

dftFunction <- function(x) {
    sumreal <- 0
    sumimag <- 0

	for (j in 1:size) {
		angle <- 6.283185 * j * x / size;
	
		real <- inputReal[i] * cos(angle) + inputImag[j] * sin(angle)
		imag <- -inputReal[i] * sin(angle) + inputImag[i] * cos(angle)
		
		sumreal <- sumreal + real
		sumimag <- sumimag + imag
    }

	result <- list(sumreal, sumimag)
	return(result)
}	

input <- 1:size
inputReal <<- runif(size)
inputImag <<- runif(size)

for (i in 1:REPETITIONS) {
	start <- nanotime()
	result <- marawacc.testGPU(input, dftFunction);
	total <- nanotime() - start
	print(total)
}


