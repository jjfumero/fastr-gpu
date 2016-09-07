
## ASTx
## Mandelbrot benchmark 

## Parse arguments
args <- commandArgs(trailingOnly=TRUE)

if (length(args) == 0) {
	stop("No input size passed. Usage: ./mandelbrotGPU.R <size> ")
} 

size <- as.integer(args[1])

REPETITIONS <- 11

CHECK_RESULT <- TRUE


mandelbrotOpenCL <- function(indexIDX, indexJDX) {
	iterations <- 10000
	space <- 2/size
	Zr <- 0
    Zi <- 0
    Cr <- (1 * indexJDX * space - 1.5)
    Ci <- (1 * indexIDX * space - 1.0)

    ZrN <- 0
    ZiN <- 0
    y <- 0

	for (i in 1:iterations) {
		if ((ZiN + ZrN) <= 4) {
			Zi <- 2.0 * Zr * Zi + Ci
    	    Zr <- 1 * ZrN - ZiN + Cr
        	ZiN <- Zi * Zi
	        ZrN <- Zr * Zr
			y <- y + 1
		} else {
			break;
		}
	}

	result <- ((y * 255) / iterations);
	return(result)
}

mandelbrotCPU <- function(indexIDX, indexJDX) {
	iterations <- 10000
	space <- 2/size
	Zr <- 0
    Zi <- 0
    Cr <- (1 * indexJDX * space - 1.5)
    Ci <- (1 * indexIDX * space - 1.0)

    ZrN <- 0
    ZiN <- 0
    y <- 0

	while ((y < iterations) && ((ZiN + ZrN) <= 4)) {
		Zi <- 2.0 * Zr * Zi + Ci;
        Zr <- 1 * ZrN - ZiN + Cr;
        ZiN <- Zi * Zi;
        ZrN <- Zr * Zr;
		y <- y + 1
	}

	result <- ((y * 255) / iterations);
	return(result)
}


totalSize <- size*size
x <<- 1:totalSize
y <<- 1:totalSize
	
if (CHECK_RESULT) {
	resultSeq <- mapply(mandelbrotCPU, x, y)
}

for (i in 1:REPETITIONS) {
	system.gc()
	start <- nanotime()
	result <- marawacc.testGPU(x, mandelbrotOpenCL, y);
	end <- nanotime()
	total <- end - start
	print(paste("Total Time:" , total))

	if (CHECK_RESULT) {
		nonError <- identical(resultSeq, result)
		if (!nonError) {
			for (i in 1:totalSize) {
				if (abs(resultSeq[i] - result[i]) > 0.1) {
					print("Result is wrong")
					break;
				}
			}
		}
	}
}

