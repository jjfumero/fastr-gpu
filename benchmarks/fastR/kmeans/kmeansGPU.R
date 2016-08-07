
## ASTx
## KMeans benchmark, baseline 

## Parse arguments
args <- commandArgs(trailingOnly=TRUE)

if (length(args) == 0) {
	stop("No input size passed. Usage: ./kmeansGPU.R <size> ")
} 

size <- as.integer(args[1])

REPETITIONS <- 20
KS <- 10

## Lambda expression for the computation
benchmark <- function(inputSize) {

	kmeansFunction <- function(x, y) {
		minDist <- 10000000
		id <- 0
		for (i in 1:KS) {
			currentDist <- (x - centre[i]) * (x - centre[i])  +
						   (y - centre[i + KS]) * (y - centre[i + KS])
			if (currentDist < minDist) {
				minDist <- currentDist
				id <- i
			}	
		}
		return(id)
	}
	
	centre <<- runif(KS*2)  * 10 * 2 - 10 
	x <- centre[1:KS]
	y <- centre[(KS+1):(KS*2)]

	for (i in 1:REPETITIONS) {
		start <- nanotime()
		result <- marawacc.gpusapply(x, kmeansFunction, y);
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

