
## ASTx
## PI benchmark 

## Parse arguments
args <- commandArgs(trailingOnly=TRUE)

if (length(args) == 0) {
	stop("No input size passed. Usage: ./piGPU.R <size> ")
} 

size <- as.integer(args[1])

REPETITIONS <- 11

## Lambda expression for the computation
benchmark <- function(inputSize) {

	piFunction <- function(x) {
		seed <- x
		sum <- 0.0
		x <- 0.12312
		y <- 0.871283971
		s <- x*x + y+y 
		if (s < 1) {
			return(1)
		} else {
			return(0)
		}
	}

	x <- 1:size;

	for (i in 1:REPETITIONS) {
		start <- nanotime()

		piMap <- marawacc.testGPU(x, piFunction)
		reductionOp <- marawacc.reduce(piMap, function(x, y) x + y, neutral=0)
		result <- marawacc.execute(reductionOp)

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

