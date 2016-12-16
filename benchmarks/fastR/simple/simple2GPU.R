
## ASTx
## NBody benchmark, baseline 

## Parse arguments
args <- commandArgs(trailingOnly=TRUE)

if (length(args) == 0) {
	stop("No input size passed. Usage: ./simpleGPU.R <size> ")
} 

size <- as.integer(args[1])

REPETITIONS <-11

NUMBODIES <- size

## Lambda expression for the computation
benchmark <- function(inputSize) {

	#this <- new.env()
	#environment(this) <- emptyenv()

	gpuFunction <- function(px) {
    	dist <- 0
	    for (i in 1:size) {
    	    x <- px * vectorData[i]
        	dist <- dist + x        
	    }   
    	return(dist)    
	}

	input <- runif(size)
		
	# if scope var appears as global -> Graal will provide a ConstantNode[]
	vectorData <<- runif(size)

	for (i in 1:REPETITIONS) {
		start <- nanotime()
		result <- marawacc.testGPU(input, gpuFunction);
		end <- nanotime()
		total <- end - start
		print(total)
	}
}

## Main
print("FASTR CPU")
print(paste("SIZE:", size))
benchmark(size)

