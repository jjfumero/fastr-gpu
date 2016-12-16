
## ASTx
## SpectralNorm benchmark 

## Parse arguments
args <- commandArgs(trailingOnly=TRUE)

if (length(args) == 0) {
	stop("No input size passed. Usage: ./spectralNormSeq.R <size> ")
} 

size <- as.integer(args[1])

REPETITIONS <-11

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
		return(sum)
	}

	spectralNorm2 <- function(i) {
		sum <- 0
		for (j in 1:size) {
			evalAPrime <- (j + i) * (j + i + 1)
			evalA <- evalAPrime / 2
			evalA <- evalA + j + 1
			evalA <- 1.0/ evalA
			partial <- evalA * v[j]
			sum <- partial + sum
		}
		return(sum)
	}

	v <<- rep(1, size)
	x <- 1:size

	for (i in 1:REPETITIONS) {
		start <- nanotime()

		resultA <- marawacc.sapply(x, spectralNorm1, nThreads=1);
		v <<- resultA
		resultB <- marawacc.sapply(resultA, spectralNorm2,  nThreads=1) 

		end <- nanotime()
		total <- end - start
		print(paste("Total Time:", total))
	}
}

## Main
print(paste("SIZE:", size))
benchmark(size)

