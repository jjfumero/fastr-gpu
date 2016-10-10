## ASTx
## Parse arguments
args <- commandArgs(trailingOnly=TRUE)

if (length(args) == 0) {
	stop("No input size passed. Usage: ./simple.R <size> ")
} 

size <- as.integer(args[1])

REPETITIONS <- 11

size <- size
DELTA <- 0.005
ESPSQRT <- 500

simpleFunction <- function(px) {
	acc1 <- 0
	for (i in 1:size) {
		r1 <- positions[i]	
        acc1 <- acc1 + r1;
    }
	result <-acc1
	return(result)	
}

benchmark <- function(inputSize) {
	px <- runif(size)
	positions <<- 1:size

	for (i in 1:size) {
		idx <- i 
		positions[idx] <<- px[i]
	}

	for (i in 1:REPETITIONS) {
		start <- nanotime()
		result <- marawacc.gpusapply(px, simpleFunction);
		end <- nanotime()
		total <- end - start
		print(total)
	}
}


## Main
print("FASTR GPU")
print(paste("SIZE:", size))
benchmark(size)

