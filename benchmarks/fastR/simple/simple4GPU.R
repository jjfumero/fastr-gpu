
## ASTx
## NBody benchmark, baseline 

## Parse arguments
args <- commandArgs(trailingOnly=TRUE)

if (length(args) == 0) {
	stop("No input size passed. Usage: ./simpleGPU.R <size> ")
} 

size <- as.integer(args[1])

REPETITIONS <- 11

gpuFunction <- function(px) {
	dist <- 0
	for (i in 1:size) {
		x <- px * vectorData[i]
		dist <- dist + x	
	}
	return(dist)	
}

input <- runif(size)
vectorData <- runif(size)

for (i in 1:size) {
	vectorData[i] <- input[i] 
}

for (i in 1:REPETITIONS) {
	start <- nanotime()
	result <- marawacc.gpusapply(input, gpuFunction);
	end <- nanotime()
	total <- end - start
	print(total)
}

