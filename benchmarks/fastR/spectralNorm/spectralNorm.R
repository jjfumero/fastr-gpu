
## ASTx
## SpectralNorm benchmark 

## Parse arguments
args <- commandArgs(trailingOnly=TRUE)

if (length(args) == 0) {
	stop("No input size passed. Usage: ./kmeans.R <size> ")
} 

size <- as.integer(args[1])

REPETITIONS <- 10

KS <- 10

## Lambda expression for the computation
benchmark <- function(inputSize) {

	spectralNorm1 <- function(i) {
		sum <- 0
		for (j in 1:size) {
			evalAPrime <- (i + j) * (i + j + 1)
			evalA <- evalAPrime / 2
			evalA <- evalA + i + 1
			evalA <- 1.0/ evalA
			partial <- evalA * v[j]
			sum <- partial + sum
		}
		result <- list(i, sum)
		return(result)
	}

	spectralNorm2 <- function(x, y) {

	}


	for (i in 1:REPETITIONS) {
		start <- nanotime()
		result <- mapply(spectralNorm1, x);
		end <- nanotime()
		total <- end - start
		print(total)
	}
}

## Main
print("FASTR CPU")
print(paste("SIZE:", size))
benchmark(size)

