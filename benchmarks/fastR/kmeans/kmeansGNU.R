
## GNU R version
## KMeans benchmark

## Parse arguments
args <- commandArgs(trailingOnly=TRUE)

if (length(args) == 0) {
	stop("No input size passed. Usage: ./kmeansGNU.R <size> ")
} 

size <- as.integer(args[1])

REPETITIONS <-11

## Number of clusters
KS <- 10

require(compiler)
enableJIT(3)

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
		start <- proc.time()
		result <- mapply(kmeansFunction, x, y);
		end <- proc.time()
		total <- end - start
		print(total)
	}
}

## Main
print("GNU R Version")
print(paste("SIZE:", size))
benchmark(size)

