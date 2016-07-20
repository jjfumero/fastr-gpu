
## ASTx
## NBody benchmark, baseline 

## Parse arguments
args <- commandArgs(trailingOnly=TRUE)

if (length(args) == 0) {
	stop("No input size passed. Usage: ./simpleGPU.R <size> ")
} 

size <- as.integer(args[1])

REPETITIONS <- 10

NUMBODIES <- size

## Lambda expression for the computation
benchmark <- function(inputSize) {

	nbodyFunction <- function(px) {
		dist <- 0
		for (i in 1:NUMBODIES) {
			body <- 4*i
			r <- rep(0, 3)
			r[1] <- vectorData[i]
			#r[1] <- vectorData[body] - px	
			#r[2] <- vectorData[body + 1] - px
			#r[3] <- vectorData[body + 2] - px	
			dist <- dist + (r[1] + r[2] + r[3])	
		}
		return(dist)	
	}

	px <- runif(size)
	vectorData <- runif(4*size)

	for (i in 1:REPETITIONS) {
		start <- nanotime()
		result <- marawacc.testGPU(px, nbodyFunction);
		end <- nanotime()
		total <- end - start
		print(total)
	}
}

## Main
print("FASTR CPU")
print(paste("SIZE:", size))
benchmark(size)

