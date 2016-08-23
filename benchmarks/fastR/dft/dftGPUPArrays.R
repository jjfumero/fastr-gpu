
## AST-X Compiler
## DFT Benchmark

## Parse arguments
args <- commandArgs(trailingOnly=TRUE)

if (length(args) == 0) {
	stop("No input size passed. Usage: ./dftGPUPArray.R <size> ")
} 

size <- as.integer(args[1])

REPETITIONS <- 11

CHECK_RESULT <- FALSE

dftGPUFunction <- function(x) {
    sumreal <- 0
    sumimag <- 0
	for (j in 1:size) {
		angle <- 6.283185 * j;
		angle <- angle * x 
		angle <- angle / size;
	
		real <- (inputReal[j] * cos(angle)) + (inputImag[j] * sin(angle))
		imag <- ((-1) * inputReal[j] * sin(angle)) + (inputImag[j] * cos(angle))
		
		sumreal <- sumreal + real
		sumimag <- sumimag + imag
    }
	result <- list(sumreal, sumimag)
	return(result)
}	

dftCPUFunction <- function(x) {
    sumreal <- 0
    sumimag <- 0
	for (j in 1:size) {
		angle <- 6.283185 * j;
		angle <- angle * x 
		angle <- angle / size;
	
		real <- (inputReal[j] * cos(angle)) + (inputImag[j] * sin(angle))
		imag <- ((-1) * inputReal[j] * sin(angle)) + (inputImag[j] * cos(angle))
		
		sumreal <- sumreal + real
		sumimag <- sumimag + imag
    }
	result <- list(sumreal, sumimag)
	return(result)
}	

input <- 1:size
inputReal <<- runif(size)
inputImag <<- runif(size)

if (CHECK_RESULT) {
	resultSeq <- mapply(dftCPUFunction, input)
}

input <- marawacc.parray(input)

for (i in 1:REPETITIONS) {
	start <- nanotime()
	result <- marawacc.testGPU(input, dftGPUFunction);
	total <- nanotime() - start
	print(paste("Total Time:" , total))

	# Check result
	if (CHECK_RESULT) {
		k <- 1
		j <- 1
		while (k < size) {
			lseq <- c(resultSeq[[k]], resultSeq[[k+1]])
			lgpu <- c(result[[j]][[1]], result[[j]][[2]])

			# check the elements for the tuple
			if (abs(lseq[1] - lgpu[1]) > 0.1) {
       			print("Result is wrong")
            	break;
            }
			if (abs(lseq[2] - lgpu[2]) > 0.1) {
    	   		print("Result is wrong")
        	    break;
            }

			k <- k + 2
			j <- j + 1
		}
	}
}


