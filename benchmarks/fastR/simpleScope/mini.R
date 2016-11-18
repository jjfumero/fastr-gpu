args <- commandArgs(trailingOnly=TRUE)

if (length(args) == 0) {
	stop("No input size passed. Usage: ./mini.R <size> ")
} 

size <- as.integer(args[1])

REPETITIONS <- 1

nbodyFunction <- function(px) {
	r1 <- positions[4] * positions[4+1]	
	return(result)	
}

benchmark <- function(inputSize) {
	px <- runif(size)
	positions <<- 1:size*2

	for (i in 1:size) {
		idx <- i * 2
		positions[idx] <<- px[i]
		positions[idx+1] <<- px[i]
	}

	for (i in 1:REPETITIONS) {
		start <- nanotime()
		result <- marawacc.gpusapply(px, nbodyFunction);
		end <- nanotime()
		total <- end - start
		print(total)
	}
}

## Main
print(paste("SIZE:", size))
benchmark(size)

