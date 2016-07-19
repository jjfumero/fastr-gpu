
## ASTx
## KMeans benchmark, baseline 

## Parse arguments
args <- commandArgs(trailingOnly=TRUE)

if (length(args) == 0) {
	stop("No input size passed. Usage: ./kmeansGPU.R <size> ")
} 

size <- as.integer(args[1])

REPETITIONS <- 100

KS <- 10

## Lambda expression for the computation
benchmark <- function(inputSize) {

	kmeansFunction <- function(x, y) {
		minDist <- -1
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

	x <- 0:size;
	y <- 0:size;
	centre <- 1:(size*2)

	for (i in 1:size) {
		idx <- i * 2
		centre[idx] <- x[i]
		centre[idx + 1] <- y[i]	
	}
	
	for (i in 1:REPETITIONS) {
		start <- nanotime()
		result <- marawacc.testGPU(x, kmeansFunction, y);
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

