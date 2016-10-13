
## GNU R
## SpectralNorm benchmark 

args <- commandArgs(trailingOnly=TRUE)

if (length(args) == 0) {
	stop("No input size passed. Usage: ./spectralNormGNU.R <size> ")
} 

size <- as.integer(args[1])

REPETITIONS <- 1

require(compiler)
enableJIT(3)

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
		start <- proc.time()

		resultA <- mapply(spectralNorm1, x);
		v <<- resultA
		resultB <- mapply(spectralNorm2, resultA) 

		end <- proc.time()
		total <- end - start
		print(total)
	}
}

## Main
print("FASTR CPU")
print(paste("SIZE:", size))
benchmark(size)

