
## ASTx
## SpectralNorm benchmark 

## Parse arguments
args <- commandArgs(trailingOnly=TRUE)

if (length(args) == 0) {
	stop("No input size passed. Usage: ./spectralNormGPU.R <size> ")
} 

size <- as.integer(args[1])

REPETITIONS <-11

CHECK_RESULT <- F

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

	spectralNorm1CPU <- function(i) {
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

	spectralNorm2CPU <- function(i) {
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

	if (CHECK_RESULT) {
		seqA <- mapply(spectralNorm1CPU, x);
		v <<- seqA
		resultSeq <- mapply(spectralNorm2CPU, seqA) 
	}	

	v <<- rep(1, size)

	for (i in 1:REPETITIONS) {
		system.gc()

		start <- nanotime()
		resultA <- marawacc.testGPU(x, spectralNorm1);
		v <<- resultA
		resultB <- marawacc.testGPU(resultA, spectralNorm2) 
		end <- nanotime()
		total <- end - start
		print(paste("Total Time:", total))

		if (CHECK_RESULT) {
			nonError <- identical(resultSeq, resultB)
			if (!nonError) {
				for (i in 1:size) {
					if (abs(resultSeq[i] - resultB[i]) > 0.5) {
						print("Result is wrong")
						break;
					}
				}
			}
		}
	}
}

## Main
print("ASTx Benchmark")
print(paste("SIZE:", size))
benchmark(size)

