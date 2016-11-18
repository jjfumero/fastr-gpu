## GNU-R
## Saxpy benchmark, baseline 

## Parse arguments
args <- commandArgs(trailingOnly=TRUE)

if (length(args) == 0) {
        stop("No input size passed. Usage: ./saxpy.R <size> ")
} 

size <- as.integer(args[1])

REPETITIONS <- 1

CHECK <- FALSE

require(compiler)
enableJIT(3)

#library(parallel)

## Lambda expression for the computation
benchmark <- function(inputSize) {

        saxpyFunction <- function(x, y) {
                result <- (alpha * x) + y;
                return (result);
        }

        alpha <- 0.12

        x <- runif(size)
        y <- runif(size)

        # Seq code
        resultSeq <- alpha * x + y
        correct <- TRUE

        for (i in 1:REPETITIONS) {
        start <- proc.time()
        result <- mapply(saxpyFunction, x, y)
        #result <- parallel::mcmapply(saxpyFunction, x, y, mc.cores = 8)
        total <- proc.time() - start
        print( total)

                # check result
                if (CHECK) {
                        nonError <- identical(resultSeq, result)
                        if (!nonError) {
                                for (i in seq(result)) {
                                        if (abs(resultSeq[i] - result[i]) > 0.1) {
                                                print(nonError)
                                                correct <- FALSE
                                                break;
                                        }
                                }
                                if (correct) {
                                        print("Result is correct")
                                }
                        } else {
                                print("Result is correct")
                        }
                        #print(result);
                }
        }
}

## Main 
print(paste("SIZE:", size))
benchmark(size)

