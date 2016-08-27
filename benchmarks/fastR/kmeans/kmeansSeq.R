
## ASTx
## KMeans benchmark

## Parse arguments
args <- commandArgs(trailingOnly=TRUE)

if (length(args) == 0) {
	stop("No input size passed. Usage: ./kmeansSeq.R <size> ")
} 

size <- as.integer(args[1])

REPETITIONS <- 11

KS <- 10

benchmark <- function(inputSize) {

	kmeansFunction <- function(x, y) {
		minDist <- 10000000
		id <- -1
		for (i in 1:KS) {
			currentDist <- (x - centre[i]) * (x - centre[i]) + 
						   (y - centre[i + KS]) * (y - centre[i + KS])
			if (currentDist < minDist) {
				minDist <- currentDist
				id <- i
			}			
		}
		return(id)
	}

	centre <<- runif(KS*2)
	x <- runif(size)
	y <- runif(size)

	for (i in 1:REPETITIONS) {
		start <- nanotime()
		result <- marawacc.sapply(x, kmeansFunction, x, nThreads=1);
		end <- nanotime()
		total <- end - start
		print(paste("Total Time:", total))
	}
}

## Main
print("FASTR CPU")
print(paste("SIZE:", size))
benchmark(size)

