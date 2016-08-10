
## ASTx
## Mandelbrot benchmark 

## Parse arguments
args <- commandArgs(trailingOnly=TRUE)

if (length(args) == 0) {
	stop("No input size passed. Usage: ./mandelbrot.R <size> ")
} 

size <- as.integer(args[1])

REPETITIONS <- 10

iterations <- 10000
space <- 2/size

## Lambda expression for the computation
benchmark <- function(inputSize) {

	mandelbrotFunction <- function(indexIDX, indexJDX) {

		Zr <- 0
        Zi <- 0;
        Cr <- (1 * indexJDX * space - 1.5)
        Ci <- (1 * indexIDX * space - 1.0);

        ZrN <- 0
        ZiN <- 0
        y <- 0

		for (i in 1:iterations) {
			if (ZiN + ZrN <= 4.0) {
				break;
			}
			Zi <- 2.0 * Zr * Zi + Ci;
            Zr <- 1 * ZrN - ZiN + Cr;
            ZiN <- Zi * Zi;
            ZrN <- Zr * Zr;
			y <- i
		}

		result <- ((y * 255) / iterations);
		return(result)
	}

	totalSize <- size*size
	x <- runif(totalSize)
	y <- runif(totalSize)
		
	for (i in 1:size) {
		for (j in 1:size) {
			x[i * size + j] <- i
			y[i * size + j] <- j
		}
	}
	
	for (i in 1:REPETITIONS) {
		start <- nanotime()
		result <- mapply(mandelbrotFunction, x, y);
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

